"""Compact live transmission telemetry panel."""

from PyQt6.QtWidgets import QFormLayout, QLabel, QFrame


class StatsPanel(QFrame):
    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._values = {}
        layout = QFormLayout(self)
        for key, label in (("frames", "Total frames"), ("state", "State"), ("throughput", "Throughput"), ("ber", "Estimated BER")):
            value = QLabel("--")
            value.setObjectName(f"stat_{key}")
            self._values[key] = value
            layout.addRow(label, value)

    def update_stats(self, *, frames: int, state: str, throughput: float, ber: float) -> None:
        self._values["frames"].setText(str(frames))
        self._values["state"].setText(state)
        self._values["throughput"].setText(f"{throughput:.1f} KB/s")
        self._values["ber"].setText(f"{ber:.4%}")
