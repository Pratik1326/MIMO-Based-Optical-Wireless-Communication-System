import hashlib

import pytest

from pc.app.data.packetizer import FileMetadata, Packetizer
from pc.app.protocol.fec import FECError, ReedSolomonFEC
from pc.app.protocol.packet import FrameType, Packet, PacketError


def test_packet_serialization_and_deserialization() -> None:
    packet = Packet(FrameType.DATA, channel_id=3, sequence_id=42, payload=b"hello")

    raw = packet.to_bytes()
    assert raw[:2] == b"MO"
    assert Packet.from_bytes(raw) == packet


def test_packet_crc_rejects_corruption() -> None:
    raw = bytearray(Packet(FrameType.SYNC, 0, 1, b"sync").to_bytes())
    raw[8] ^= 0x01

    with pytest.raises(PacketError, match="CRC mismatch"):
        Packet.from_bytes(bytes(raw))


def test_reed_solomon_reconstructs_corrupted_symbols() -> None:
    fec = ReedSolomonFEC.rs_255_223()
    original = bytes(range(223))
    encoded = bytearray(fec.encode(original))
    for index in (0, 25, 100, 222, 240, 254):
        encoded[index] ^= 0xA5

    assert fec.decode(bytes(encoded)) == original


def test_reed_solomon_reports_uncorrectable_data() -> None:
    fec = ReedSolomonFEC(parity_symbols=8)
    encoded = bytearray(fec.encode(b"payload"))
    for index in range(5):
        encoded[index] ^= 0xFF

    with pytest.raises(FECError):
        fec.decode(bytes(encoded))


def test_packetizer_adds_metadata_and_respects_payload_size() -> None:
    source = b"optical data" * 100
    packets = list(Packetizer(payload_size=64, channel_id=2).packetize(source))

    assert packets
    assert all(packet.frame_type is FrameType.DATA for packet in packets)
    assert all(len(packet.payload) <= 64 for packet in packets)
    metadata = FileMetadata.from_bytes(packets[0].payload)
    assert metadata.size == len(source)
    assert metadata.sha256 == hashlib.sha256(source).digest()


def test_packetize_file_does_not_drop_repeated_chunks(tmp_path) -> None:
    source = b"same chunk" * 100
    path = tmp_path / "source.bin"
    path.write_bytes(source)

    packets = list(Packetizer(payload_size=64).packetize_file(path))
    first_metadata = FileMetadata.from_bytes(packets[0].payload)
    reconstructed = packets[0].payload[44:] + b"".join(packet.payload for packet in packets[1:])

    assert first_metadata.size == len(source)
    assert reconstructed == source


def test_file_metadata_zfil_magic() -> None:
    digest = hashlib.sha256(b"content").digest()
    meta = FileMetadata(size=12345, sha256=digest, magic=b"ZFIL")
    assert meta.is_compressed is True
    raw = meta.to_bytes()
    restored = FileMetadata.from_bytes(raw)
    assert restored.is_compressed is True
    assert restored.size == 12345
    assert restored.sha256 == digest
    assert restored.magic == b"ZFIL"


def test_packetizer_compression_reduces_packets_and_reconstructs_original() -> None:
    import zlib

    # 10 KB of compressible repetitive pattern (similar to bitmap/document)
    source = b"MIMO_OPTICAL_WIRELESS_COMMUNICATION_HIGH_THROUGHPUT_DATA_BLOCK_" * 160
    assert len(source) > 10000

    # Uncompressed packets
    uncompressed_packets = list(Packetizer(payload_size=128, compress=False).packetize(source))
    # Compressed packets
    compressed_packets = list(Packetizer(payload_size=128, compress=True).packetize(source))

    # Compression must drastically reduce the number of packets
    assert len(compressed_packets) < len(uncompressed_packets) // 4

    # Verify first packet header
    first_meta = FileMetadata.from_bytes(compressed_packets[0].payload)
    assert first_meta.is_compressed is True
    assert first_meta.magic == b"ZFIL"
    assert first_meta.size == len(source)
    assert first_meta.sha256 == hashlib.sha256(source).digest()

    # Reconstruct compressed payload stream
    compressed_stream = compressed_packets[0].payload[44:] + b"".join(p.payload for p in compressed_packets[1:])
    decompressed = zlib.decompress(compressed_stream)
    assert decompressed == source


def test_packetizer_compression_falls_back_when_incompressible() -> None:
    import os

    # High-entropy random data that cannot be compressed
    source = os.urandom(1024)
    packets = list(Packetizer(payload_size=128, compress=True).packetize(source))

    # Should fall back to standard FILE magic rather than expanding
    first_meta = FileMetadata.from_bytes(packets[0].payload)
    assert first_meta.is_compressed is False
    assert first_meta.magic == b"FILE"
    assert first_meta.size == len(source)
    reconstructed = packets[0].payload[44:] + b"".join(p.payload for p in packets[1:])
    assert reconstructed == source

