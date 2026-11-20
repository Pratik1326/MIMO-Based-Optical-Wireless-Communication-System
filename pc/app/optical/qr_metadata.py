"""Receiver-compatible QR handshake metadata and image generation."""

from __future__ import annotations

from dataclasses import asdict, dataclass
import json
from uuid import uuid4

import numpy as np
import qrcode
from qrcode.constants import ERROR_CORRECT_Q


@dataclass(frozen=True, slots=True)
class QRMetadata:
    protocol_version: int
    filename: str
    file_size: int
    total_packets: int
    mimo_mode: str
    symbol_mode: str
    packet_size: int
    session_id: str

    def to_dict(self) -> dict[str, object]:
        return asdict(self)

    def to_compact_dict(self) -> dict[str, object]:
        return {
            "v": self.protocol_version,
            "f": self.filename,
            "s": self.file_size,
            "p": self.total_packets,
            "m": self.mimo_mode,
            "y": self.symbol_mode,
            "z": self.packet_size,
            "id": self.session_id,
        }

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), separators=(",", ":"))

    def to_compact_json(self) -> str:
        return json.dumps(self.to_compact_dict(), separators=(",", ":"))


def create_metadata(
    filename: str,
    file_size: int,
    total_packets: int,
    mimo_mode: str,
    packet_size: int,
    protocol_version: int = 1,
    symbol_mode: str = "BINARY",
) -> QRMetadata:
    return QRMetadata(
        protocol_version=protocol_version,
        filename=filename,
        file_size=file_size,
        total_packets=total_packets,
        mimo_mode=mimo_mode,
        symbol_mode=symbol_mode.upper(),
        packet_size=packet_size,
        session_id=f"SESS-{uuid4().hex[:4].upper()}",
    )


def generate_qr(metadata: QRMetadata, box_size: int = 12, border: int = 4, compact: bool = True) -> np.ndarray:
    """Return a grayscale uint8 QR image containing the exact metadata JSON."""
    if box_size <= 0 or border < 0:
        raise ValueError("box_size must be positive and border cannot be negative")
    qr = qrcode.QRCode(
        version=None,
        error_correction=ERROR_CORRECT_Q,
        box_size=box_size,
        border=border,
    )
    qr.add_data(metadata.to_compact_json() if compact else metadata.to_json())
    qr.make(fit=True)
    image = qr.make_image(fill_color="black", back_color="white").convert("L")
    return np.asarray(image, dtype=np.uint8).copy()

