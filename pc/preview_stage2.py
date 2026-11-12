"""Generate a Stage 2 optical frame preview."""

from pathlib import Path
import sys

import cv2

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pc.app.data.packetizer import Packetizer
from pc.app.mimo.scheduler import MimoScheduler
from pc.app.optical.frame_generator import FrameGenerator, FrameRenderConfig
from pc.app.optical.symbol_encoder import SymbolEncoder, SymbolMode


OUTPUT = Path(__file__).with_name("test_frame_output.png")


def main() -> None:
    source = (b"Stage 2 MIMO optical preview payload. " * 32)
    packets = list(Packetizer(payload_size=128).packetize(source))
    scheduler = MimoScheduler("2x2")
    queues = scheduler.schedule(packets)
    encoder = SymbolEncoder(cell_size=4, padding=1)
    matrices = {
        channel_id: encoder.encode(b"".join(packet.payload for packet in queue) or b"0", SymbolMode.BINARY)
        for channel_id, queue in queues.items()
    }
    frame = FrameGenerator(FrameRenderConfig(width=800, height=800, layout="2x2")).render(matrices)
    if not cv2.imwrite(str(OUTPUT), frame):
        raise RuntimeError(f"failed to write {OUTPUT}")
    print(f"Wrote {OUTPUT} ({frame.shape[1]}x{frame.shape[0]} BGR)")


if __name__ == "__main__":
    main()
