"""Reversible binary and differential Manchester optical symbol matrices."""

from __future__ import annotations

from enum import Enum
import math

import cv2
import numpy as np


class SymbolMode(str, Enum):
    BINARY = "BINARY"
    MANCHESTER = "MANCHESTER"

    @classmethod
    def parse(cls, value: "SymbolMode | str") -> "SymbolMode":
        if isinstance(value, cls):
            return value
        normalized = value.upper()
        if normalized in {"BINARY", "MANCHESTER"}:
            return cls(normalized)
        raise ValueError("symbol mode must be BINARY or MANCHESTER")


class SymbolEncoder:
    """Encode bytes into square, cell-scaled matrices and decode them."""

    def __init__(self, cell_size: int = 8, padding: int = 1) -> None:
        if cell_size <= 0 or padding < 0:
            raise ValueError("cell_size must be positive and padding cannot be negative")
        self.cell_size = cell_size
        self.padding = padding

    def encode(
        self,
        data: bytes,
        mode: SymbolMode | str = SymbolMode.BINARY,
        min_bytes: int = 0,
    ) -> np.ndarray:
        selected = SymbolMode.parse(mode)
        raw = bytes(data)
        if min_bytes > 0 and len(raw) < min_bytes:
            # Deterministic pseudo-random padding sequence to prevent periodic vertical striping
            padding_len = min_bytes - len(raw)
            # Whitening sequence derived from alternating bit shifts
            padding_bytes = bytes(((i * 137 + 0x5A) ^ 0xA5) & 0xFF for i in range(padding_len))
            raw = raw + padding_bytes
        symbols = self._symbols_from_bytes(raw, selected)
        side = max(1, math.ceil(math.sqrt(len(symbols))))
        grid = np.zeros((side + self.padding * 2, side + self.padding * 2), dtype=np.uint8)
        grid[self.padding : self.padding + side, self.padding : self.padding + side] = 0
        grid[self.padding : self.padding + side, self.padding : self.padding + side].flat[: len(symbols)] = symbols
        grid = cv2.resize(grid, None, fx=self.cell_size, fy=self.cell_size, interpolation=cv2.INTER_NEAREST)
        return grid * 255

    def decode(
        self,
        matrix: np.ndarray,
        data_length: int,
        mode: SymbolMode | str = SymbolMode.BINARY,
    ) -> bytes:
        selected = SymbolMode.parse(mode)
        if data_length < 0:
            raise ValueError("data_length cannot be negative")
        grid = self._to_intensity_grid(matrix)
        usable_side = grid.shape[0] - self.padding * 2
        if usable_side <= 0:
            raise ValueError("matrix is smaller than configured padding")
        symbols = grid[self.padding : self.padding + usable_side, self.padding : self.padding + usable_side].flat
        required = data_length * 8
        return self._bytes_from_symbols(list(symbols)[:required * (2 if selected is SymbolMode.MANCHESTER else 1)], selected, data_length)

    def _symbols_from_bytes(self, data: bytes, mode: SymbolMode) -> np.ndarray:
        bits = np.unpackbits(np.frombuffer(data, dtype=np.uint8))
        if mode is SymbolMode.BINARY:
            return bits
        pairs = np.empty(bits.size * 2, dtype=np.uint8)
        pairs[0::2] = bits
        pairs[1::2] = 1 - bits
        return pairs

    def _to_intensity_grid(self, matrix: np.ndarray) -> np.ndarray:
        array = np.asarray(matrix)
        if array.ndim == 2:
            intensity = array.astype(np.float32)
        elif array.ndim == 3 and array.shape[2] == 3:
            intensity = array.mean(axis=2).astype(np.float32)
        else:
            raise ValueError("matrix must be a grayscale or BGR NumPy array")
        height, width = intensity.shape[:2]
        if height != width or height % self.cell_size:
            raise ValueError("matrix must be square and divisible by cell_size")
        side = height // self.cell_size
        return cv2.resize(intensity, (side, side), interpolation=cv2.INTER_AREA)

    def _bytes_from_symbols(self, symbols: list[float], mode: SymbolMode, data_length: int) -> bytes:
        values = np.asarray(symbols, dtype=np.float32)
        if mode is SymbolMode.MANCHESTER:
            if len(values) % 2:
                raise ValueError("Manchester symbol stream must contain pairs")
            values = (values[0::2] > values[1::2]).astype(np.uint8)
        else:
            values = (values >= 128).astype(np.uint8)
        bits = np.asarray(values, dtype=np.uint8)
        return np.packbits(bits).tobytes()[:data_length]
