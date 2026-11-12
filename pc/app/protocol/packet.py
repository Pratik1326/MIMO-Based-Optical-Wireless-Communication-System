"""Binary packet format for the optical transmission protocol."""

from __future__ import annotations

from dataclasses import dataclass
from enum import IntEnum
import struct
import zlib


MAGIC = b"MO"
PROTOCOL_VERSION = 0x01
_HEADER = struct.Struct(">2sBBBIH")
_CRC = struct.Struct(">I")


class PacketError(ValueError):
    """Raised when a serialized packet is invalid."""


class FrameType(IntEnum):
    IDLE = 0
    CALIB = 1
    SYNC = 2
    CONFIG = 3
    DATA = 4
    END = 5


@dataclass(frozen=True, slots=True)
class Packet:
    """A packet with a CRC-protected protocol header and payload."""

    frame_type: FrameType
    channel_id: int
    sequence_id: int
    payload: bytes = b""
    version: int = PROTOCOL_VERSION

    def __post_init__(self) -> None:
        if not 0 <= self.channel_id <= 0xFF:
            raise PacketError("channel_id must fit in one byte")
        if not 0 <= self.sequence_id <= 0xFFFFFFFF:
            raise PacketError("sequence_id must fit in uint32")
        if not 0 <= self.version <= 0xFF:
            raise PacketError("version must fit in one byte")
        if not isinstance(self.frame_type, FrameType):
            raise PacketError("frame_type must be a FrameType")
        if len(self.payload) > 0xFFFF:
            raise PacketError("payload cannot exceed 65535 bytes")

    def to_bytes(self) -> bytes:
        header = _HEADER.pack(
            MAGIC,
            self.version,
            int(self.frame_type),
            self.channel_id,
            self.sequence_id,
            len(self.payload),
        )
        return header + self.payload + _CRC.pack(zlib.crc32(header + self.payload) & 0xFFFFFFFF)

    @classmethod
    def from_bytes(cls, raw: bytes) -> "Packet":
        if len(raw) < _HEADER.size + _CRC.size:
            raise PacketError("packet is shorter than its fixed header and CRC")

        header = raw[: _HEADER.size]
        magic, version, frame_value, channel_id, sequence_id, payload_length = _HEADER.unpack(header)
        expected_size = _HEADER.size + payload_length + _CRC.size
        if len(raw) != expected_size:
            raise PacketError("packet length does not match payload_length")
        if magic != MAGIC:
            raise PacketError("invalid packet magic")
        try:
            frame_type = FrameType(frame_value)
        except ValueError as exc:
            raise PacketError(f"unknown frame type: {frame_value}") from exc

        payload = raw[_HEADER.size : _HEADER.size + payload_length]
        received_crc = _CRC.unpack(raw[-_CRC.size :])[0]
        calculated_crc = zlib.crc32(raw[:-_CRC.size]) & 0xFFFFFFFF
        if received_crc != calculated_crc:
            raise PacketError("CRC mismatch")

        return cls(frame_type, channel_id, sequence_id, payload, version)

    serialize = to_bytes
    deserialize = from_bytes
