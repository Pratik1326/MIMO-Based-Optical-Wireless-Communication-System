"""CRC-32 implementation used by protocol packets."""

import zlib


def crc32(data: bytes) -> int:
    return zlib.crc32(data) & 0xFFFFFFFF
