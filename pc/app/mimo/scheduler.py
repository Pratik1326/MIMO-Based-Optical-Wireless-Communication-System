"""Round-robin scheduler for spatial optical channels."""

from __future__ import annotations

from collections import deque
from collections.abc import Iterable
from dataclasses import dataclass, replace

from ..protocol.packet import Packet
from .splitter import SpatialLayout, channel_position, split_round_robin


@dataclass(slots=True)
class SpatialChannel:
    channel_id: int
    row: int
    column: int
    queue: deque[Packet]

    @property
    def active(self) -> bool:
        return bool(self.queue)

    @property
    def status(self) -> str:
        return "active" if self.active else "idle"


class MimoScheduler:
    def __init__(self, layout: SpatialLayout | str = SpatialLayout.SISO) -> None:
        self.layout = SpatialLayout.parse(layout)
        self.channels = {
            channel_id: SpatialChannel(
                channel_id,
                *channel_position(channel_id, self.layout),
                deque(),
            )
            for channel_id in range(self.layout.channel_count)
        }

    @property
    def channel_count(self) -> int:
        return self.layout.channel_count

    def schedule(self, packets: Iterable[Packet]) -> dict[int, list[Packet]]:
        for channel in self.channels.values():
            channel.queue.clear()
        queues = split_round_robin(packets, self.channel_count)
        for channel_id, scheduled in enumerate(queues):
            self.channels[channel_id].queue.extend(
                replace(packet, channel_id=channel_id) for packet in scheduled
            )
        return self.queues

    @property
    def queues(self) -> dict[int, list[Packet]]:
        return {channel_id: list(channel.queue) for channel_id, channel in self.channels.items()}

    @property
    def channel_status(self) -> dict[int, str]:
        return {channel_id: channel.status for channel_id, channel in self.channels.items()}

    def next_packet(self, channel_id: int) -> Packet | None:
        try:
            channel = self.channels[channel_id]
        except KeyError as exc:
            raise ValueError("channel_id is outside the selected layout") from exc
        return channel.queue.popleft() if channel.queue else None
