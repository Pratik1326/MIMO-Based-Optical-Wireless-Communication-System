"""Reed-Solomon forward error correction helpers."""

from __future__ import annotations

from reedsolo import RSCodec, ReedSolomonError


class FECError(ValueError):
    """Raised when FEC configuration or reconstruction fails."""


class ReedSolomonFEC:
    """Encode and decode byte streams with configurable parity symbols."""

    def __init__(self, parity_symbols: int = 32) -> None:
        if not 1 <= parity_symbols <= 254:
            raise FECError("parity_symbols must be between 1 and 254")
        self.parity_symbols = parity_symbols
        self._codec = RSCodec(parity_symbols)

    @classmethod
    def rs_255_223(cls) -> "ReedSolomonFEC":
        return cls(parity_symbols=32)

    def encode(self, data: bytes) -> bytes:
        return bytes(self._codec.encode(bytes(data)))

    def decode(self, encoded: bytes) -> bytes:
        try:
            result = self._codec.decode(bytes(encoded))
        except ReedSolomonError as exc:
            raise FECError("Reed-Solomon reconstruction failed") from exc
        return bytes(result[0] if isinstance(result, tuple) else result)


encode = ReedSolomonFEC().encode
