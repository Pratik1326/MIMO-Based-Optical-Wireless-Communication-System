import numpy as np
import json

from pc.app.optical.frame_generator import FrameGenerator, FrameRenderConfig
from pc.app.optical.qr_metadata import create_metadata, generate_qr
from pc.app.optical.state_machine import TransmitterState, TransmissionStateMachine
from pc.app.optical.timing import frame_interval_ns, simulate_deadlines


def test_compensated_deadlines_have_sub_millisecond_quantization_error() -> None:
    deadlines = simulate_deadlines(60, 500)
    interval = frame_interval_ns(60)
    errors = [abs((deadline - deadlines[0]) - index * interval) for index, deadline in enumerate(deadlines)]

    assert max(errors) < 1_000_000
    assert all(deadlines[index + 1] - deadlines[index] == interval for index in range(499))


def test_state_machine_supports_qr_standby_and_transmission() -> None:
    machine = TransmissionStateMachine()
    seen = []
    machine.state_changed.connect(lambda old, new: seen.append((old, new)))

    assert machine.prepare() is TransmitterState.WAITING_FOR_RECEIVER
    assert machine.start() is TransmitterState.TRANSMITTING
    assert machine.pause() is TransmitterState.TRANSMITTING
    assert machine.paused
    assert machine.resume() is TransmitterState.TRANSMITTING
    assert not machine.paused
    assert machine.stop() is TransmitterState.WAITING_FOR_RECEIVER
    assert [new for _, new in seen] == [
        TransmitterState.TRANSMITTING,
        TransmitterState.WAITING_FOR_RECEIVER,
    ]


def test_state_machine_emits_only_while_transmitting() -> None:
    machine = TransmissionStateMachine()
    frame = np.zeros((8, 8, 3), dtype=np.uint8)
    emitted = []
    machine.frame_ready.connect(lambda *_: emitted.append(True))

    assert not machine.emit_frame(frame)
    machine.prepare()
    assert not machine.emit_frame(frame)
    machine.start()
    assert machine.emit_frame(frame)
    machine.pause()
    assert machine.paused
    assert not machine.emit_frame(frame)
    assert emitted == [True]


def test_qr_metadata_matches_android_schema() -> None:
    metadata = create_metadata("sample_test.txt", 1024, 100, "2x2", 10)
    payload = json.loads(metadata.to_json())

    assert list(payload) == [
        "protocol_version", "filename", "file_size", "total_packets",
        "mimo_mode", "symbol_mode", "packet_size", "session_id",
    ]
    assert payload["protocol_version"] == 1
    assert payload["symbol_mode"] == "BINARY"
    assert payload["session_id"].startswith("SESS-")
    assert len(payload["session_id"]) == 9
    assert generate_qr(metadata).ndim == 2


def test_frame_generator_has_qr_and_data_phases() -> None:
    generator = FrameGenerator(FrameRenderConfig(width=480, height=480, layout="2x2", marker_size=48))
    qr = generate_qr(create_metadata("x", 1, 1, "2x2", 1))
    qr_frame = generator.render_qr(qr)
    data_frame = generator.render({0: np.zeros((8, 8), dtype=np.uint8)})

    assert qr_frame.shape == data_frame.shape == (576, 576, 3)
    assert not np.array_equal(qr_frame, data_frame)


def test_pdf_metadata_qr_framing_and_decoding() -> None:
    import cv2
    from pc.app.mimo.splitter import SpatialLayout

    meta = create_metadata("lecture_04_mimo_optical_systems_report.pdf", 4500000, 17578, "2x2", 256)
    qr = generate_qr(meta)
    generator = FrameGenerator(FrameRenderConfig(width=800, height=800, layout=SpatialLayout.GRID_2X2))
    frame = generator.render_qr(qr)

    # Verify frame size with 10% safe zone margin
    assert frame.shape == (960, 960, 3)

    # Verify that QR detector detects and decodes the metadata perfectly
    detector = cv2.QRCodeDetector()
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    decoded_text, pts, _ = detector.detectAndDecode(gray)
    if not decoded_text:
        margin = int(frame.shape[0] * 0.10)
        h, w = frame.shape[:2]
        decoded_text, pts, _ = detector.detectAndDecode(gray[margin:h-margin, margin:w-margin])

    assert bool(decoded_text)
    decoded_json = json.loads(decoded_text)
    filename = decoded_json.get("filename") or decoded_json.get("f")
    total_packets = decoded_json.get("total_packets") or decoded_json.get("p")
    file_size = decoded_json.get("file_size") or decoded_json.get("s")
    packet_size = decoded_json.get("packet_size") or decoded_json.get("z")
    mimo_mode = decoded_json.get("mimo_mode") or decoded_json.get("m")
    assert filename == "lecture_04_mimo_optical_systems_report.pdf"
    assert total_packets == 17578
    assert file_size == 4500000
    assert packet_size == 256
    assert mimo_mode == "2x2"


