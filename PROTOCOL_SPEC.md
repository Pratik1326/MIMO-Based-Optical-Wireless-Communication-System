# MIMO Optical Communication Protocol Specification

Version: 2.0 | Updated: 2026-10-30

This document defines the canonical wire, modulation, framing, and bidirectional synchronization contract between the PC Optical Transmitter and the Android Mobile Optical Receiver.

---

## 1. System Operating Modes

The system supports two operating modes:
1. **Closed-Loop Bidirectional Optical Mode (Default & Recommended):**
   - **Forward Link (PC Screen $\to$ Smartphone Camera):** High-speed spatial optical checkerboard symbol matrices framed by four corner ArUco tracking anchors.
   - **Reverse Link (Smartphone Flashlight $\to$ PC Webcam):** Low-frequency optical feedback pulse trains for automated session coordination (READY auto-start, NACK retransmit, ACK completion).
2. **Open-Loop Simplex Mode:**
   - The transmitter cycles data frames indefinitely in an endless carousel. The receiver independently locks onto the screen and reassembles packets without feedback.

---

## 2. Packet Wire Format

All multi-byte integer fields are unsigned and encoded in **Big-Endian (Network Byte Order)**.

| Byte Offset | Field Length | Field Name | Data Type | Value / Definition |
|:---:|:---:|:---|:---:|:---|
| 0 | 2 | Magic Bytes | ASCII | `b"MO"` (`0x4D, 0x4F`) |
| 2 | 1 | Protocol Version | uint8 | `0x01` |
| 3 | 1 | Frame Type | uint8 | `0=IDLE`, `1=CALIB`, `2=SYNC`, `3=CONFIG`, `4=DATA`, `5=END` |
| 4 | 1 | Spatial Channel ID | uint8 | Channel index (`0` for 1x1; `0..3` for 2x2; `0..15` for 4x4) |
| 5 | 4 | Sequence ID | uint32 | Monotonic sequence number ($0 \dots \text{total\_packets}-1$) |
| 9 | 2 | Payload Length | uint16 | Payload size $N$ in bytes ($64 \le N \le 512$) |
| 11 | $N$ | Payload Bytes | bytes | Raw chunk payload (Packet #0 includes 44-byte FileMetadata) |
| $11 + N$ | 4 | CRC-32 Checksum | uint32 | Standard IEEE 802.3 CRC-32 computed over bytes $0 \dots 10 + N$ |

- **Header Size:** Fixed 11 bytes (`_HEADER = struct.Struct(">2sBBBIH")`).
- **CRC-32:** Fixed 4 bytes (`_CRC = struct.Struct(">I")`). Uses standard polynomial `0x04C11DB7` (reflected `0xEDB88320`), initialized with `0xFFFFFFFF` and post-inverted with `0xFFFFFFFF` (standard `zlib.crc32`).
- **Total Overhead:** 15 bytes per packet.

---

## 3. Packet #0 FileMetadata Header

To ensure self-describing file delivery, the `Packetizer` prepends a fixed **44-byte metadata header** to the start of Packet #0:

```
0                   1                   2                   3
0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                 Magic Bytes ("FILE" or "ZFIL")                |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                 Original File Size (uint64)                   +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                                                               +
|                                                               |
+                 SHA-256 Digest (32 bytes)                     +
|                                                               |
+                                                               +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

- **Magic:**
  - `b"FILE"`: Payload is uncompressed raw bytes.
  - `b"ZFIL"`: Payload was compressed using zlib level 6 (`zlib.compress()`). The receiver decompresses using `java.util.zip.Inflater`.
- **Original File Size:** 8-byte uint64 storing exact uncompressed byte length. The receiver truncates any padding bytes past this boundary.
- **SHA-256 Digest:** 32-byte cryptographic hash of the entire uncompressed file.

---

## 4. Pre-Flight QR Handshake Schema

Before optical transmission begins, the PC renders a high-contrast centered QR code containing session metadata:

```json
{
  "protocol_version": 1,
  "filename": "document.pdf",
  "file_size": 24576,
  "total_packets": 96,
  "mimo_mode": "2x2",
  "symbol_mode": "BINARY",
  "packet_size": 256,
  "session_id": "SESS-E412"
}
```

A compact JSON format is also supported for faster QR scanning in low-resolution conditions:
```json
{"v":1,"f":"document.pdf","s":24576,"p":96,"m":"2x2","y":"BINARY","z":256,"id":"SESS-E412"}
```

---

## 5. Reverse Optical Link (Flashlight Feedback)

The receiver signals state to the PC webcam using structured torch pulses:

| Signal | Pulse Pattern | Timing Specifications | PC Transmitter Response |
|:---|:---|:---|:---|
| **READY** | 1 pulse | 280ms ON | Transitions PC to `TRANSMITTING` |
| **NACK** | 2 pulses | 220ms ON, 250ms OFF | Rewinds carousel index by `stride * 2` |
| **ACK** | 1 sustained strobe + 2 pulses | 850ms ON, 250ms OFF, $2 \times$ (200ms ON, 220ms OFF) | Stops transmission and returns to standby |

The PC detector uses:
- Saturated pixel cluster counter ($I \ge 245$) inside the ROI.
- P98 lens flare percentile tracking to tolerate off-axis and tilted angles.
- Dynamic ambient baseline tracking with hysteresis.

---

## 6. Spatial MIMO Channel Layout & Strides

### Layout Geometry
- **1x1 SISO:** Channel 0 occupies the entire central data area ($688 \times 688\text{px}$).
- **2x2 MIMO:** 4 quadrants:
  ```text
  [ Channel 0 (TL) ][ Channel 1 (TR) ]
  [ Channel 2 (BL) ][ Channel 3 (BR) ]
  ```
- **4x4 MIMO:** 16 tiles arranged in row-major order ($0 \dots 15$).

### Transmission Strides
- **Stride 4 (Pure Parallel Throughput):** Channels 0, 1, 2, 3 transmit 4 distinct consecutive packets per frame.
- **Stride 2 (Space-Time Redundancy):** Channels 0 & 1 transmit current packets; Channels 2 & 3 repeat previous packets.
- **Stride 1 (Sliding Carousel):** Advances 1 packet per frame across all quadrants.

---

## 7. Physical Geometry & Calibration

- **Base Canvas:** $800 \times 800$ pixels.
- **Safe Zone Border:** 10% black margin ($80\text{px}$) on all sides $\to 960 \times 960$ total frame.
- **ArUco Corner Anchors (`DICT_4X4_50`):**
  - Marker Size: $44\text{px}$.
  - Quiet Margin: $6\text{px}$ white quiet zone.
  - Clockwise placement: ID 0 = TL, ID 1 = TR, ID 2 = BR, ID 3 = BL.
- **Central Data Area:** $X, Y \in [56, 744]\text{px}$ ($688 \times 688\text{px}$).
- **Inner Marker Span:** $[50, 750]\text{px}$ ($700\text{px}$).
- **Receiver Homography Mapping:** Destination points are offset outward by $\approx 4.465\text{px}$ in $512 \times 512$ space to map exactly to the data grid.

---

## 8. Modulation & Dynamic Thresholding

- **Modulation:** `BINARY` (On-Off Keying).
- **Whitening Padding:** Derived from pseudo-random alternating bit shifts `((i * 137 + 0x5A) ^ 0xA5) & 0xFF` to maintain uniform grid dimensions across terminal packets.
- **Receiver Demodulation:**
  1. Applies CLAHE contrast restoration.
  2. Samples the inner 25% core of each cell (eliminating transition boundary blur).
  3. Computes the decision threshold using iterative 2-Means dynamic clustering.

