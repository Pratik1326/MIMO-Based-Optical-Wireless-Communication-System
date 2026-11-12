"""File selection and transmission configuration page."""

from dataclasses import dataclass
from pathlib import Path

from PyQt6.QtWidgets import QComboBox, QFileDialog, QFormLayout, QLabel, QPushButton, QSpinBox, QVBoxLayout, QWidget


@dataclass(frozen=True, slots=True)
class SendConfig:
    path: Path
    payload_size: int
    mimo_mode: str
    symbol_mode: str
    fps: int


class SendPage(QWidget):
    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        from PyQt6.QtCore import QSettings
        self.settings = QSettings("MIMO", "OpticalTransmitter")
        self.selected_path: Path | None = None
        root = QVBoxLayout(self)
        form = QFormLayout()
        self.file_label = QLabel("No file selected")
        browse = QPushButton("Choose file")
        browse.clicked.connect(self.choose_file)
        self.payload_size = QSpinBox()
        self.payload_size.setRange(64, 512)
        self.payload_size.setSingleStep(64)
        default_payload = int(self.settings.value("default_payload_size", 256))
        self.payload_size.setValue(default_payload)
        self.mimo_mode = QComboBox()
        self.mimo_mode.addItems(["1x1", "2x2", "4x4"])
        default_mimo = self.settings.value("default_mimo_mode", "1x1", str)
        self.mimo_mode.setCurrentText(default_mimo)
        self.symbol_mode = QComboBox()
        self.symbol_mode.addItem("BINARY")
        self.fps = QComboBox()
        self.fps.addItems(["1", "2", "4", "5", "8", "10", "12", "15", "20"])
        default_fps = self.settings.value("default_fps", "10", str)
        self.fps.setCurrentText(default_fps)
        self.mimo_stride = QComboBox()
        self.mimo_stride.addItem("4 - Pure Parallel (Maximum Speed)", 4)
        self.mimo_stride.addItem("2 - Space-Time Redundancy", 2)
        self.mimo_stride.addItem("1 - Sliding Carousel", 1)
        stride = int(self.settings.value("mimo_stride", 4))
        for idx in range(self.mimo_stride.count()):
            if self.mimo_stride.itemData(idx) == stride:
                self.mimo_stride.setCurrentIndex(idx)
                break
        form.addRow("File", self.file_label)
        form.addRow("", browse)
        form.addRow("Payload size", self.payload_size)
        form.addRow("MIMO mode", self.mimo_mode)
        form.addRow("MIMO stride", self.mimo_stride)
        form.addRow("Symbol mode", self.symbol_mode)
        form.addRow("Frame rate", self.fps)
        root.addLayout(form)
        self.start_button = QPushButton("Start Transmission")
        self.start_button.setEnabled(False)
        root.addWidget(self.start_button)
        root.addStretch()

    def reload_defaults(self) -> None:
        self.payload_size.setValue(int(self.settings.value("default_payload_size", 256)))
        self.mimo_mode.setCurrentText(self.settings.value("default_mimo_mode", "2x2", str))
        self.fps.setCurrentText(self.settings.value("default_fps", "10", str))
        stride = int(self.settings.value("mimo_stride", 4))
        for idx in range(self.mimo_stride.count()):
            if self.mimo_stride.itemData(idx) == stride:
                self.mimo_stride.setCurrentIndex(idx)
                break

    def choose_file(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self,
            "Select transmission file",
            "",
            "All Supported Files (*.png *.jpg *.jpeg *.pdf *.txt *.bin *.zip *.json *.csv);;Images (*.png *.jpg *.jpeg);;Documents (*.pdf *.txt *.json *.csv);;All Files (*.*)",
        )
        if path:
            self.set_file(path)

    def set_file(self, path: str | Path) -> None:
        self.selected_path = Path(path)
        self.file_label.setText(self.selected_path.name)
        self.start_button.setEnabled(True)

    def configuration(self) -> SendConfig:
        if self.selected_path is None:
            raise ValueError("select a file before starting transmission")
        self.settings.setValue("mimo_stride", self.mimo_stride.currentData())
        return SendConfig(self.selected_path, self.payload_size.value(), self.mimo_mode.currentText(), self.symbol_mode.currentText(), int(self.fps.currentText()))
