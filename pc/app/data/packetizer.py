"""Convert source bytes into CRC-protected optical data packets."""

from __future__ import annotations

from dataclasses import dataclass
import struct
import zlib
from pathlib import Path

from ..protocol.packet import FrameType, Packet
from .checksum import sha256_bytes
from .file_reader import inspect_file

_METADATA = struct.Struct(">4sQ32s")
_METADATA_MAGIC = b"FILE"
_COMPRESSED_MAGIC = b"ZFIL"


@dataclass(frozen=True, slots=True)
class FileMetadata:
    size: int
    sha256: bytes
    magic: bytes = _METADATA_MAGIC

    @property
    def is_compressed(self) -> bool:
        return self.magic == _COMPRESSED_MAGIC

    def to_bytes(self) -> bytes:
        if len(self.sha256) != 32:
            raise ValueError("sha256 must contain 32 bytes")
        return _METADATA.pack(self.magic, self.size, self.sha256)

    @classmethod
    def from_bytes(cls, raw: bytes) -> "FileMetadata":
        if len(raw) < _METADATA.size:
            raise ValueError("metadata is truncated")
        magic, size, digest = _METADATA.unpack(raw[: _METADATA.size])
        if magic not in (_METADATA_MAGIC, _COMPRESSED_MAGIC):
            raise ValueError("invalid file metadata magic")
        return cls(size, digest, magic)


class Packetizer:
    def __init__(
        self,
        payload_size: int = 256,
        channel_id: int = 0,
        sequence_start: int = 0,
        compress: bool = False,
    ) -> None:
        if not 64 <= payload_size <= 512:
            raise ValueError("payload_size must be between 64 and 512 bytes")
        if not 0 <= channel_id <= 255:
            raise ValueError("channel_id must fit in one byte")
        if not 0 <= sequence_start <= 0xFFFFFFFF:
            raise ValueError("sequence_start must fit in uint32")
        self.payload_size = payload_size
        self.channel_id = channel_id
        self.sequence_start = sequence_start
        self.compress = compress

    def packetize(self, data: bytes):
        source = bytes(data)
        original_size = len(source)
        digest = sha256_bytes(source)

        if self.compress and original_size > 64:
            compressed = zlib.compress(source, level=6)
            if len(compressed) < original_size:
                stream_to_chunk = compressed
                magic = _COMPRESSED_MAGIC
            else:
                stream_to_chunk = source
                magic = _METADATA_MAGIC
        else:
            stream_to_chunk = source
            magic = _METADATA_MAGIC

        metadata = FileMetadata(original_size, digest, magic=magic).to_bytes()
        first_capacity = self.payload_size - len(metadata)
        chunks = [stream_to_chunk[:first_capacity]]
        chunks.extend(
            stream_to_chunk[offset : offset + self.payload_size]
            for offset in range(first_capacity, len(stream_to_chunk), self.payload_size)
        )
        if not stream_to_chunk:
            chunks = [b""]
        for offset, chunk in enumerate(chunks):
            payload = metadata + chunk if offset == 0 else chunk
            yield Packet(FrameType.DATA, self.channel_id, self.sequence_start + offset, payload)

    def packetize_file(self, path: str | Path):
        source = Path(path).read_bytes()
        yield from self.packetize(source)
