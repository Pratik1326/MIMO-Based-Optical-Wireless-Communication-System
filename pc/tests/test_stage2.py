import cv2
import numpy as np
import pytest
from pc.app.mimo.scheduler import MimoScheduler
from pc.app.optical.frame_generator import FrameGenerator, FrameRenderConfig
from pc.app.optical.symbol_encoder import SymbolEncoder, SymbolMode
from pc.app.protocol.packet import FrameType, Packet


@pytest.mark.parametrize(
    ("layout", "expected_count"),
    (("1x1", 1), ("2x2", 4), ("4x4", 16)),
)
def test_scheduler_distributes_packets_round_robin(layout: str, expected_count: int) -> None:
    packets = [Packet(FrameType.DATA, 0, index, bytes([index])) for index in range(17)]
    scheduler = MimoScheduler(layout)
    queues = scheduler.schedule(packets)

    assert scheduler.channel_count == expected_count
    assert [len(queues[channel_id]) for channel_id in range(expected_count)] == [
        (17 + expected_count - channel_id - 1) // expected_count for channel_id in range(expected_count)
    ]
    assert all(packet.channel_id == channel_id for channel_id, queue in queues.items() for packet in queue)
    assert scheduler.channel_status[0] == "active"
    assert scheduler.next_packet(0) is not None


def test_scheduler_tracks_idle_channels() -> None:
    scheduler = MimoScheduler("2x2")
    scheduler.schedule([Packet(FrameType.DATA, 0, 0, b"one")])

    assert scheduler.channel_status == {0: "active", 1: "idle", 2: "idle", 3: "idle"}


def test_binary_symbol_encoding_is_reversible() -> None:
    data = b"MIMO Stage 2"
    encoder = SymbolEncoder(cell_size=3, padding=1)

    matrix = encoder.encode(data, SymbolMode.BINARY)
    decoded = encoder.decode(matrix, len(data), SymbolMode.BINARY)

    assert matrix.shape[0] == matrix.shape[1]
    assert matrix.shape[0] % 3 == 0
    assert decoded == data


def test_frame_has_shape_borders_grid_and_four_aruco_markers() -> None:
    matrices = {
        0: np.full((8, 8), 32, dtype=np.uint8),
        1: np.full((8, 8), 96, dtype=np.uint8),
        2: np.full((8, 8), 160, dtype=np.uint8),
        3: np.full((8, 8), 224, dtype=np.uint8),
    }
    config = FrameRenderConfig(width=480, height=480, layout="2x2", marker_size=48, marker_margin=12)
    frame = FrameGenerator(config).render(matrices)

    assert frame.shape == (576, 576, 3)
    assert frame.dtype == np.uint8
    assert np.array_equal(frame[0, 0], [0, 0, 0])
    assert np.array_equal(frame[48, 48], [255, 255, 255])
    assert np.array_equal(frame[288, 288], [0, 0, 0])

    dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)
    detector = cv2.aruco.ArucoDetector(dictionary, cv2.aruco.DetectorParameters())
    _, ids, _ = detector.detectMarkers(frame)

    assert ids is not None
    assert set(ids.ravel()) == {0, 1, 2, 3}
