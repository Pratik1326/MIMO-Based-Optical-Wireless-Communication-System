"""Spatial layout and round-robin packet splitting."""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from enum import Enum
from typing import TypeVar


T = TypeVar("T")


class SpatialLayout(str, Enum):
    SISO = "1x1"
    GRID_2X2 = "2x2"
    GRID_4X4 = "4x4"

    @property
    def side_length(self) -> int:
        return int(self.value[0])

    @property
    def channel_count(self) -> int:
        return self.side_length * self.side_length

    @classmethod
    def parse(cls, value: "SpatialLayout | str") -> "SpatialLayout":
        if isinstance(value, cls):
            return value
        try:
            return cls(value)
        except ValueError as exc:
            raise ValueError("layout must be one of: 1x1, 2x2, 4x4") from exc


def channel_position(channel_id: int, layout: SpatialLayout | str) -> tuple[int, int]:
    selected = SpatialLayout.parse(layout)
    if not 0 <= channel_id < selected.channel_count:
        raise ValueError("channel_id is outside the selected layout")
    return divmod(channel_id, selected.side_length)


def split_round_robin(items: Iterable[T], channel_count: int) -> list[list[T]]:
    if channel_count <= 0:
        raise ValueError("channel_count must be positive")
    queues = [[] for _ in range(channel_count)]
    for index, item in enumerate(items):
        queues[index % channel_count].append(item)
    return queues
