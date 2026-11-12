"""Run the Stage 3 Qt/OpenGL transmission demonstration."""

from __future__ import annotations

from pathlib import Path
import sys

import numpy as np
from PyQt6.QtCore import QObject, Qt, pyqtSlot
from PyQt6.QtGui import QSurfaceFormat
from PyQt6.QtWidgets import QApplication

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pc.app.mimo.scheduler import MimoScheduler
from pc.app.optical.frame_generator import FrameGenerator, FrameRenderConfig
from pc.app.optical.gl_renderer import OpticalGLRenderer
from pc.app.optical.state_machine import TransmissionState, TransmissionStateMachine
from pc.app.optical.symbol_encoder import SymbolEncoder, SymbolMode
from pc.app.optical.timing import PrecisionTimer
from pc.app.protocol.packet import FrameType, Packet


class _FramePresenter(QObject):
    """Present worker-thread frames safely on the Qt GUI thread."""

    def __init__(self, renderer: OpticalGLRenderer) -> None:
        super().__init__(renderer)
        self.renderer = renderer

    @pyqtSlot(object, int)
    def present(self, frame: np.ndarray, _frame_index: int) -> None:
        self.renderer.set_frame(frame)
        self.renderer.update()


def main() -> int:
    format_ = QSurfaceFormat()
    format_.setSwapInterval(1)
    QSurfaceFormat.setDefaultFormat(format_)
    app = QApplication(sys.argv)
    renderer = OpticalGLRenderer()
    renderer.setWindowTitle("MIMO Optical Transmission Demo (60 FPS)")
    renderer.resize(800, 800)
    renderer.show()

    state = TransmissionStateMachine()
    state.prepare()
    state.start()
    scheduler = MimoScheduler("2x2")
    packets = [Packet(FrameType.DATA, 0, index, bytes([index]) * 32) for index in range(24)]
    queues = scheduler.schedule(packets)
    encoder = SymbolEncoder(cell_size=4, padding=1)
    frame_generator = FrameGenerator(FrameRenderConfig(layout="2x2"))
    timer = PrecisionTimer(60)

    presenter = _FramePresenter(renderer)
    state.frame_ready.connect(presenter.present, Qt.ConnectionType.QueuedConnection)

    def render_tick(index: int, _timestamp: float, _diagnostic: object) -> None:
        matrices = {
            channel_id: encoder.encode(
                b"".join(packet.payload for packet in queue) + bytes([index & 0xFF]),
                SymbolMode.BINARY,
            )
            for channel_id, queue in queues.items()
        }
        state.emit_frame(frame_generator.render(matrices))

    timer.frame_tick.connect(render_tick)
    timer.start_clock()

    closed = False

    def close() -> None:
        nonlocal closed
        if closed:
            return
        closed = True
        try:
            timer.stop_clock()
        except RuntimeError:
            pass
        if state.state is TransmissionState.TRANSMITTING:
            state.stop()
        app.quit()

    renderer.destroyed.connect(close)
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
