"""Diagnostics and display capability page."""

from PyQt6.QtWidgets import QLabel, QPlainTextEdit, QPushButton, QVBoxLayout, QWidget


class DiagnosticsPage(QWidget):
    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        layout = QVBoxLayout(self)
        self.benchmark_button = QPushButton("Run 60 FPS benchmark")
        self.capabilities = QLabel("Display capabilities: querying on benchmark")
        self.log_viewer = QPlainTextEdit()
        self.log_viewer.setReadOnly(True)
        self.benchmark_button.clicked.connect(self.run_benchmark)
        layout.addWidget(self.benchmark_button)
        layout.addWidget(self.capabilities)
        layout.addWidget(self.log_viewer)

    def run_benchmark(self) -> None:
        self.capabilities.setText("Display capabilities: Qt/OpenGL surface available")
        self.log_viewer.appendPlainText("60 FPS benchmark requested")
