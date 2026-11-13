from pathlib import Path

import pytest
from PyQt6.QtWidgets import QApplication

from pc.app.ui.main_window import MainWindow


@pytest.fixture(scope="module")
def qapp():
    app = QApplication.instance() or QApplication([])
    yield app
    app.processEvents()


def test_main_window_has_all_pages_and_dark_theme(qapp) -> None:
    window = MainWindow()

    assert window.pages.count() == 5
    assert [window.tabs.tabText(index) for index in range(5)] == ["Home", "Send", "Transmission", "Settings", "Diagnostics"]
    assert qapp.style().objectName().lower() == "fusion"
    window.close()


def test_send_page_file_selection_enables_transmission(qapp, tmp_path: Path) -> None:
    source = tmp_path / "payload.txt"
    source.write_bytes(b"hello optical UI")
    window = MainWindow()

    assert not window.send_page.start_button.isEnabled()
    window.send_page.set_file(source)
    assert window.send_page.start_button.isEnabled()
    config = window.send_page.configuration()
    assert config.path == source
    assert config.payload_size == 256
    assert config.mimo_mode == "1x1"
    window.start_transmission()
    qapp.processEvents()

    assert window.tabs.currentIndex() == 2
    assert window.transmission_page.state_machine.state.value == "WAITING_FOR_RECEIVER"
    assert window.transmission_page.metadata is not None
    assert window.transmission_page.metadata.symbol_mode == "BINARY"
    assert window.transmission_page.qr_image is not None
    assert window.transmission_page.qr_image.size > 0
    assert window.acknowledgement_label.text() == "Receiver acknowledgement: NOT AVAILABLE"
    window.transmission_page.stop_transmission()
    window.close()


def test_settings_and_diagnostics_controls(qapp) -> None:
    window = MainWindow()

    assert window.settings_page.aruco_enabled.isChecked()
    window.settings_page.parity_ratio.setValue(64)
    assert window.settings_page.parity_ratio.value() == 64
    window.diagnostics_page.run_benchmark()
    assert "benchmark" in window.diagnostics_page.log_viewer.toPlainText().lower()
    window.close()


def test_endless_carousel_wraps_packet_buffer(qapp, tmp_path: Path) -> None:
    source = tmp_path / "carousel.bin"
    source.write_bytes(bytes(range(84)))
    window = MainWindow()
    window.transmission_page.configure(source, payload_size=64, mimo_mode="2x2", symbol_mode="BINARY", fps=60)
    page = window.transmission_page
    page.state_machine.start()

    page._render_tick(0, 0.0, None)
    assert page.current_packet == 1
    assert page.carousel_cycle == 0
    page._render_tick(1, 0.0, None)
    assert page.current_packet == 2
    assert page.carousel_cycle == 1
    page._render_tick(2, 0.0, None)
    assert page.current_packet == 1
    assert page.carousel_cycle == 1
    assert page.state_machine.state.value == "TRANSMITTING"
    page.stop_transmission()
    window.close()


def test_mimo_2x2_end_to_end_optical_render_and_quadrant_demodulation(qapp) -> None:
    import cv2, json, numpy as np
    from pc.app.optical.qr_metadata import create_metadata, generate_qr
    from pc.app.optical.frame_generator import FrameGenerator, FrameRenderConfig
    from pc.app.optical.symbol_encoder import SymbolEncoder, SymbolMode
    from pc.app.protocol.packet import Packet, FrameType

    fg = FrameGenerator(FrameRenderConfig(width=800, height=800, layout="2x2"))

    # 1. Verify Phase 1 QR Handshake under simulated 768x768 perspective warp
    meta = create_metadata("MIMO_Test2.txt", 884, 4, "2x2", 128)
    qr = generate_qr(meta)
    qr_frame = fg.render_qr(qr)
    inner_qr = qr_frame[136:824, 136:824]
    w768_qr = cv2.resize(cv2.cvtColor(inner_qr, cv2.COLOR_BGR2GRAY), (768, 768), interpolation=cv2.INTER_LINEAR)
    detector = cv2.QRCodeDetector()
    text, _, _ = detector.detectAndDecode(w768_qr)
    assert bool(text)
    parsed = json.loads(text)
    assert (parsed.get("filename") or parsed.get("f")) == "MIMO_Test2.txt"
    assert (parsed.get("total_packets") or parsed.get("p")) == 4
    assert (parsed.get("mimo_mode") or parsed.get("m")) == "2x2"
    assert (parsed.get("packet_size") or parsed.get("z")) == 128

    # 2. Verify Phase 2 2x2 Spatial Channel Multiplexing under simulated 768x768 warp
    enc = SymbolEncoder(cell_size=8, padding=1)
    packets = [
        Packet(FrameType.DATA, channel_id=ch, sequence_id=ch, payload=f"Quadrant_{ch}_payload_data".encode())
        for ch in range(4)
    ]
    matrices = {
        ch: enc.encode(packets[ch].to_bytes(), SymbolMode.BINARY, min_bytes=143)
        for ch in range(4)
    }
    data_frame = fg.render(matrices)

    # Simulated phone camera un-warp to 768x768
    inner_data = data_frame[136:824, 136:824]
    w768_data = cv2.resize(cv2.cvtColor(inner_data, cv2.COLOR_BGR2GRAY), (768, 768), interpolation=cv2.INTER_LINEAR)

    # Slice into 4 quadrants (384x384 each)
    half = 384
    quadrants = [
        w768_data[:half, :half],
        w768_data[:half, half:],
        w768_data[half:, :half],
        w768_data[half:, half:]
    ]

    data_side = 34
    grid_side = 36
    cell_size = 384 / grid_side
    inset = cell_size * 0.25

    for ch_idx, quad in enumerate(quadrants):
        bits = []
        for r in range(1, data_side + 1):
            y0 = int(r * cell_size + inset)
            y1 = int((r + 1) * cell_size - inset)
            for c in range(1, data_side + 1):
                x0 = int(c * cell_size + inset)
                x1 = int((c + 1) * cell_size - inset)
                cell_mean = np.mean(quad[y0:y1, x0:x1])
                bits.append(1 if cell_mean > 127 else 0)

        byte_arr = bytearray()
        for b in range(0, len(bits) - 7, 8):
            byte_val = 0
            for bit_i in range(8):
                byte_val = (byte_val << 1) | bits[b + bit_i]
            byte_arr.append(byte_val)

        expected_pkt_len = 11 + len(packets[ch_idx].payload) + 4
        decoded_pkt = Packet.from_bytes(bytes(byte_arr[:expected_pkt_len]))
        assert decoded_pkt.channel_id == ch_idx
        assert decoded_pkt.sequence_id == ch_idx
        assert decoded_pkt.payload == packets[ch_idx].payload

