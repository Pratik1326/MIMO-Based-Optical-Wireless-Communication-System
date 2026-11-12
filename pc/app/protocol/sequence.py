"""Monotonic uint32 sequence numbers."""


class SequenceCounter:
    def __init__(self, initial: int = 0) -> None:
        if not 0 <= initial <= 0xFFFFFFFF:
            raise ValueError("initial must fit in uint32")
        self._value = initial

    def next(self) -> int:
        value = self._value
        self._value = (self._value + 1) & 0xFFFFFFFF
        return value
