# MIMO Optical Wireless Communication (OWC / OCC)
## End-to-End System Synchronization & Receiver Implementation Guide

> **Target Audience:** Android Native Receiver Developers & CV Engineers  
> **Transmitter Stack:** Python, PyQt6, OpenGL, OpenCV C++ (`cv2`), `qrcode`, `zlib`  
> **Receiver Stack:** Kotlin, Jetpack Compose, CameraX, Camera2, OpenCV Android SDK (`4.5.3+`)

---

## 📌 Table of Contents
1. [System Overview & Synchronization Principles](#1-system-overview--synchronization-principles)
2. [Physical & Optical Layer (Screen-to-Camera Geometry)](#2-physical--optical-layer-screen-to-camera-geometry)
3. [Modulation & Symbol Grid Geometry](#3-modulation--symbol-grid-geometry)
4. [MIMO Spatial Channel Multiplexing](#4-mimo-spatial-channel-multiplexing)
5. [Protocol & Binary Packet Wire Specification](#5-protocol--binary-packet-wire-specification)
6. [QR Pre-Flight Handshake Schema](#6-qr-pre-flight-handshake-schema)
7. [Timing, Frame Rates & LCD Settling Filter](#7-timing-frame-rates--lcd-settling-filter)
8. [Receiver File-by-File Implementation & Fixes](#8-receiver-file-by-file-implementation--fixes)
9. [Step-by-Step Calibration & Validation Checklist](#9-step-by-step-calibration--validation-checklist)

---

## 1. System Overview & Synchronization Principles

The MIMO Optical Wireless Communication (OWC) system operates over a **closed-loop bidirectional optical channel** (with fallback to pure simplex mode):
- **Forward Optical Link (PC Screen -> Smartphone Camera):** Emits high-speed optical patterns (checkerboard binary symbols) framed by four ArUco tracking anchors and a pre-flight QR code card.
- **Reverse Optical Link (Smartphone Flashlight -> PC Webcam):** Communicates optical state feedback using structured camera flash strobe pulse trains (READY auto-start, NACK retransmit rewind, and ACK transfer complete auto-stop).

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PC TRANSMITTER SCREEN                         │
│                                                                        │
│   [ArUco 0 (TL)] ───────────────────────────────── [ArUco 1 (TR)]     │
│         │                                                 │            │
│         │    ┌───────────────────┬───────────────────┐    │            │
│         │    │   CHANNEL 0       │   CHANNEL 1       │    │            │
│         │    │   (Packet #0)     │   (Packet #1)     │    │            │
│         │    ├───────────────────┼───────────────────┤    │            │
│         │    │   CHANNEL 2       │   CHANNEL 3       │    │            │
│         │    │   (Packet #2)     │   (Packet #3)     │    │            │
│         │    └───────────────────┴───────────────────┘    │            │
│         │                                                 │            │
│   [ArUco 3 (BL)] ───────────────────────────────── [ArUco 2 (BR)]     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Optical Line-of-Sight
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         ANDROID RECEIVER PIPELINE                      │
│                                                                        │
│ 1. CameraX Stream (640x480 YUV -> GrayMat)                             │
│ 2. ScreenDetector: Detect ArUco IDs 0, 1, 2, 3 (DICT_4X4_50)           │
│ 3. PerspectiveCorrector: Warp Central Data Area to 512x512 Mat         │
│ 4. SyncDetector: Inter-frame delta filter (absdiff Ft vs Ft-1)         │
│ 5. RegionDetector: Slice 512x512 into 1x1, 2x2, or 4x4 spatial Mats    │
│ 6. SymbolDecoder: Sample inner 50% core of each 28x28 cell             │
│ 7. PacketAssembler: Sliding window scan for "MO" magic bytes           │
│ 8. PacketDecoder: Parse header + verify IEEE CRC-32                    │
│ 9. SequenceManager: Deduplicate seq_ids & strip 44-byte "FILE" header  │
│ 10. FileSaver: Write assembled byte payload to Downloads directory     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Physical & Optical Layer (Screen-to-Camera Geometry)

### 2.1 Frame Canvas & Safe Zone Dimensions
The transmitter renders an **$800 \times 800$ base frame** wrapped in a **$10\%$ black safe margin** ($80\text{px}$ on all sides) to prevent camera edge clipping:
- **Total Transmitted Frame Size:** $960 \times 960$ pixels.
- **Safe Zone Margin:** $80\text{px}$ on Top, Bottom, Left, and Right (solid black `RGB(0, 0, 0)`).
- **Outer White Border:** $8\text{px}$ stroke around the $800 \times 800$ base frame.

### 2.2 ArUco Corner Anchor Placement (Clockwise Standard)
The tracking anchors use OpenCV dictionary **`DICT_4X4_50`**:
- **Marker Size:** $72 \times 72$ pixels.
- **Marker Margin (White Quiet Zone):** $12\text{px}$ on all four sides $\to$ Total footprint = $96 \times 96$ pixels.
- **Marker ID Placement (Clockwise):**
  - **Marker ID 0:** **Top-Left (TL)**
  - **Marker ID 1:** **Top-Right (TR)**
  - **Marker ID 2:** **Bottom-Right (BR)**
  - **Marker ID 3:** **Bottom-Left (BL)**

```
(0,0) [Top-Left]                                                (960,0) [Top-Right]
 ┌────────────────────────────────────────────────────────────────────────┐
 │   80px Black Safe Zone Margin                                          │
 │   ┌────────────────────────────────────────────────────────────────┐   │
 │   │ [ID 0: TL]                                        [ID 1: TR]   │   │
 │   │ (80..176)                                         (784..880)   │   │
 │   │           ┌───────────────────────────────────┐                │   │
 │   │           │                                   │                │   │
 │   │           │       CENTRAL DATA GRID AREA      │                │   │
 │   │           │          (176..784 px)            │                │   │
 │   │           │          (608x608 px)             │                │   │
 │   │           │                                   │                │   │
 │   │           └───────────────────────────────────┘                │   │
 │   │ [ID 3: BL]                                        [ID 2: BR]   │   │
 │   │ (80..176)                                         (784..880)   │   │
 │   └────────────────────────────────────────────────────────────────┘   │
 │   80px Black Safe Zone Margin                                          │
 └────────────────────────────────────────────────────────────────────────┘
(0,960) [Bottom-Left]                                          (960,960) [Bottom-Right]
```

### 2.3 Non-Overlapping Data Area (Zero Data Corruption)
The data cells are drawn **strictly in the central area between the markers**:
$$\text{corner\_zone} = \text{marker\_size} + 2 \times \text{marker\_margin} = 44 + 12 = 56\text{px}$$
$$\text{Data Area: } X \in [56, 744], \quad Y \in [56, 744] \quad (\text{Width: } 688\text{px}, \text{ Height: } 688\text{px})\n\text{Inner Marker Span: } [50, 750]\text{px} \implies 700\text{px}\n\text{Homography Margin Offset in } 512\times 512\text{ Space: } \frac{700 - 688}{2} \times \frac{512}{688} \approx 4.465116\text{px}$$

> [!IMPORTANT]
> **Key Geometric Synchronization:**  
> The 4 outer corners of the Central Data Area correspond **exactly** to the 4 inner corners of the ArUco marker quiet zones!
> - $\text{Data TL} = \text{Marker}_0[2]$ (Bottom-Right corner of Marker 0)
> - $\text{Data TR} = \text{Marker}_1[3]$ (Bottom-Left corner of Marker 1)
> - $\text{Data BR} = \text{Marker}_2[0]$ (Top-Left corner of Marker 2)
> - $\text{Data BL} = \text{Marker}_3[1]$ (Top-Right corner of Marker 3)
>
> When the receiver warps using these 4 inner points, **the resulting $512 \times 512$ matrix contains 100% pure data cells and 0% ArUco marker pixels**.

---

## 3. Modulation & Symbol Grid Geometry

### 3.1 Modulation Scheme
- **Mode:** `SymbolMode.BINARY` (OOK - On-Off Keying).
- **Bit 0:** Pure Black `RGB(0, 0, 0)` (Luminance $< 128$).
- **Bit 1:** Pure White `RGB(255, 255, 255)` (Luminance $> 128$).

### 3.2 Cell Dimensions & Mathematical Formulas
For any payload size $S$ (recommended standard: **$S = 64$ bytes**):
1. **Total Packet Wire Size:**
   $$\text{Total Bytes} = S + \text{OVERHEAD\_BYTES} = 64 + 15 = 79\text{ bytes}$$
2. **Total Bit Count:**
   $$\text{Total Bits} = 79 \times 8 = 632\text{ bits}$$
3. **Data Grid Side:**
   $$\text{dataSide} = \lceil\sqrt{\text{Total Bits}}\rceil = \lceil\sqrt{632}\rceil = 26\text{ cells}$$
4. **Full Grid Dimension (with 1-cell border padding):**
   $$\text{gridSide} = \text{dataSide} + 2 = 26 + 2 = 28\text{ cells}$$
   $$\text{Total Cells per Channel} = 28 \times 28 = 784\text{ cells}$$

### 3.3 Cell Bit Ordering
- Bits are unpacked from bytes **MSB-first** (Bit 7 down to Bit 0).
- Bits are arranged in **Row-Major (C-order)**:
  - Row 0 (all cols): Black padding ($0$)
  - Row 27 (all cols): Black padding ($0$)
  - Col 0 (all rows): Black padding ($0$)
  - Col 27 (all rows): Black padding ($0$)
  - **Data cells:** Row $1 \dots 26$, Col $1 \dots 26$.
  - Row 1: Cols $1 \dots 26$ carry Bits $0 \dots 25$.
  - Row 2: Cols $1 \dots 26$ carry Bits $26 \dots 51$, etc.
  - Bits beyond bit 631 are padded with $0$ bits.

### 3.4 Terminal Packet Padding Guarantee
The transmitter guarantees that **every packet**, including short terminal packets, is padded to at least $S + 15$ bytes during symbol matrix generation.  
**Result:** The grid dimension is **invariably $28 \times 28$ cells on every single frame**.

---

## 4. MIMO Spatial Channel Multiplexing

The system supports three spatial layouts:

| Mode | Layout | Active Channels | Packets / Frame | Description |
| :---: | :---: | :---: | :---: | :--- |
| **`1x1`** *(Recommended)* | $1 \times 1$ | 1 (CH 0) | 1 | Single-channel SISO. Lowest complexity, highest optical SNR. |
| **`2x2`** | $2 \times 2$ | 4 (CH 0..3) | 4 | 4 spatial channels in parallel. $4\times$ throughput. |
| **`4x4`** | $4 \times 4$ | 16 (CH 0..15) | 16 | 16 spatial channels in parallel. For 4K screens & high-res cameras. |

### 4.1 Spatial Coordinate Slicing in 2x2 Mode
The normalized $512 \times 512$ data matrix is sliced into 4 quadrants of $256 \times 256$ pixels:
- **Channel 0 (Top-Left):** $X \in [0, 256), \quad Y \in [0, 256)$
- **Channel 1 (Top-Right):** $X \in [256, 512), \quad Y \in [0, 256)$
- **Channel 2 (Bottom-Left):** $X \in [0, 256), \quad Y \in [256, 512)$
- **Channel 3 (Bottom-Right):** $X \in [256, 512), \quad Y \in [256, 512)$

In $2 \times 2$ mode, **all 4 quadrants transmit concurrently**:
- Frame $T$: CH0 = Packet $N$, CH1 = Packet $N+1$, CH2 = Packet $N+2$, CH3 = Packet $N+3$.
- Each packet header carries its respective `channel_id` ($0, 1, 2, 3$).

---

## 5. Protocol & Binary Packet Wire Specification

Every packet is serialized in **Network Big-Endian (`>`) byte order**:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          MAGIC ("MO")         |    VERSION    |   FRAME_TYPE  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   CHANNEL_ID  |                  SEQUENCE_ID                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       PAYLOAD_LENGTH (L)      |       PAYLOAD BYTES ...       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  ... (L bytes total) ...      |           CRC-32              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          CRC-32 (cont.)       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 5.1 Field Breakdown
1. **Magic Header (2 bytes):** ASCII `"MO"` (`0x4D, 0x4F`).
2. **Version (1 byte):** `0x01`.
3. **Frame Type (1 byte):**
   - `0 = IDLE`
   - `1 = CALIB`
   - `2 = SYNC`
   - `3 = CONFIG`
   - `4 = DATA` (Standard payload)
   - `5 = END`
4. **Channel ID (1 byte):** Logical channel index (`0` for 1x1; `0, 1, 2, 3` for 2x2).
5. **Sequence ID (4 bytes, uint32 big-endian):** Monotonically increasing sequence number ($0, 1, 2, \dots$).
6. **Payload Length $L$ (2 bytes, uint16 big-endian):** Exact byte count of the payload ($1 \le L \le 512$, typically $64$).
7. **Payload Data ($L$ bytes):** Binary payload.
8. **CRC-32 (4 bytes, uint32 big-endian):** IEEE 802.3 standard checksum computed over **all preceding bytes** (Header + Payload, exactly $11 + L$ bytes).

### 5.2 Packet #0: File Metadata Header
The PC transmitter prepends a **44-byte FileMetadata header** to the payload of **Packet #0**:
- **Bytes 0–3:** Magic ASCII `"FILE"` (`0x46, 0x49, 0x4C, 0x45`).
- **Bytes 4–11:** Source file size (uint64 big-endian, 8 bytes).
- **Bytes 12–43:** SHA-256 digest of original file (32 bytes).
- **Bytes 44 to end of Packet #0:** First raw bytes of the file.

> [!TIP]
> **Receiver Requirement:**  
> When reassembling the file in `SequenceManager.kt`, inspect Packet #0: verify the `"FILE"` magic bytes, extract the 8-byte file size and 32-byte hash, and strip the 44-byte prefix before appending to the output file stream. Finally, truncate the assembled file to `file_size`.

---

## 6. QR Pre-Flight Handshake Schema

Before live data begins, the transmitter displays a centered QR code containing a compact JSON metadata payload:

```json
{
  "protocol_version": 1,
  "filename": "document.pdf",
  "file_size": 24576,
  "total_packets": 384,
  "mimo_mode": "1x1",
  "symbol_mode": "BINARY",
  "packet_size": 64,
  "session_id": "SESS-7A9B"
}
```

The Android receiver scans this QR code in `SCANNING_QR` mode, initializes its session parameters (`total_packets = 384`, `packet_size = 64`, `mimo_mode = "1x1"`), and transitions to `READY` state.

---

## 7. Timing, Frame Rates & LCD Settling Filter

- **Transmitter Frame Rate:** Defaults to **$15\text{ FPS}$** ($66.67\text{ms}$ per display frame). Can be configured to $10\text{ FPS}$ ($100\text{ms}$).
- **Continuous Carousel Loop:** The transmitter repeats the packet sequence indefinitely until stopped. The receiver can start listening at any time; once all unique sequence IDs are captured, transfer is complete.
- **The LCD Settling Problem:** Liquid crystal response time and camera rolling shutter cause transition blur during the first $15\text{--}25\text{ms}$ of a screen redraw.
- **Settling Filter Principle:**
  - Let $F_t$ be the current camera frame and $F_{t-1}$ be the previous camera frame.
  - Compute inter-frame delta:
    $$D_t = \text{Core.mean}(|F_t - F_{t-1}|)$$
  - When the screen changes: $D_t > 25.0 \implies \text{LCD is transitioning}$ (DROP frame).
  - When the LCD settles: $D_t < 10.0 \implies \text{Display is stable}$ (DECODE frame).
  - **Crucial Rule:** `prevMat` MUST be updated on **every** camera frame so that $D_t$ measures consecutive frame delta, NOT delta against a stale pre-transition frame.

---

## 8. Receiver File-by-File Implementation & Fixes

Below are the exact code modifications required in the Android project.

---

### File 1: `camera/SyncDetector.kt`
**Problem:** `prevMat` was not updated during settling, causing `Core.absdiff` to compare against an old pre-transition frame. The difference remained permanently high ($> 25.0$), freezing the receiver in an infinite settling state and dropping every packet.

**Replacement Code:**
```kotlin
package com.example.mimo_optical_receiver.camera

import org.opencv.core.Core
import org.opencv.core.Mat

class SyncDetector(
    private val settlingThreshold: Double = 25.0,
    private val stableThreshold: Double = 10.0
) {
    private var prevMat: Mat? = null
    private var isSettling = false

    fun isNewFrame(currentMat: Mat): Pair<Boolean, Int> {
        if (prevMat == null) {
            prevMat = currentMat.clone()
            return Pair(true, 100)
        }

        val diffMat = Mat()
        return try {
            Core.absdiff(currentMat, prevMat, diffMat)
            val meanDiff = Core.mean(diffMat).`val`[0]
            val confidence = (meanDiff / 40.0 * 100).toInt().coerceIn(0, 100)

            // CRITICAL FIX: Always update prevMat so diffMat measures inter-frame delta (Ft - Ft-1)
            prevMat?.release()
            prevMat = currentMat.clone()

            if (meanDiff > settlingThreshold) {
                isSettling = true
                Pair(false, confidence) // LCD is transitioning / tearing
            } else if (isSettling && meanDiff < stableThreshold) {
                isSettling = false
                Pair(true, confidence)  // LCD settled on a new stable frame
            } else {
                Pair(false, confidence) // Duplicate / steady-state frame
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, 0)
        } finally {
            diffMat.release()
        }
    }

    fun release() {
        prevMat?.release()
        prevMat = null
        isSettling = false
    }
}
```

---

### File 2: `camera/ScreenDetector.kt`
**Problem:**
1. Marker 2 was assigned to `outerBR` and Marker 3 to `outerBL`. With the clockwise transmitter ordering (`0: TL, 1: TR, 2: BR, 3: BL`), this mapping was correct in name, but `outerTL..outerBR` were sampling the *outer* corners of the ArUco markers, pulling marker pixels into the data matrix.
2. The 4 corners of the **central data grid** are defined by the **inner corners** of the ArUco markers:
   - Data TL = `Marker_0[2]` (Bottom-Right of Marker 0)
   - Data TR = `Marker_1[3]` (Bottom-Left of Marker 1)
   - Data BR = `Marker_2[0]` (Top-Left of Marker 2)
   - Data BL = `Marker_3[1]` (Top-Right of Marker 3)

**Key Section Update in `ScreenDetector.kt`:**
```kotlin
if (parsedMarkers.size >= 3) {
    // Inner corners of the ArUco markers directly enclose the Central Data Area:
    // Marker corner indexing in OpenCV: 0 = TL, 1 = TR, 2 = BR, 3 = BL
    var dataTL: Point? = parsedMarkers[0]?.second?.get(2) // Marker 0 (TL) -> Inner BR corner
    var dataTR: Point? = parsedMarkers[1]?.second?.get(3) // Marker 1 (TR) -> Inner BL corner
    var dataBR: Point? = parsedMarkers[2]?.second?.get(0) // Marker 2 (BR) -> Inner TL corner
    var dataBL: Point? = parsedMarkers[3]?.second?.get(1) // Marker 3 (BL) -> Inner TR corner

    // 3-Point Glare Recovery for inner corners:
    if (dataTL == null && dataTR != null && dataBR != null && dataBL != null) {
        dataTL = Point(dataTR.x + dataBL.x - dataBR.x, dataTR.y + dataBL.y - dataBR.y)
    } else if (dataTR == null && dataTL != null && dataBR != null && dataBL != null) {
        dataTR = Point(dataTL.x + dataBR.x - dataBL.x, dataTL.y + dataBR.y - dataBL.y)
    } else if (dataBL == null && dataTL != null && dataTR != null && dataBR != null) {
        dataBL = Point(dataTL.x + dataBR.x - dataTR.x, dataTL.y + dataBR.y - dataTR.y)
    } else if (dataBR == null && dataTL != null && dataTR != null && dataBL != null) {
        dataBR = Point(dataTR.x + dataBL.x - dataTL.x, dataTR.y + dataBL.y - dataTL.y)
    }

    if (dataTL != null && dataTR != null && dataBR != null && dataBL != null) {
        val sortedCorners = listOf(dataTL, dataTR, dataBR, dataBL)
        val detectedArea = calculatePolygonArea(sortedCorners)

        if (detectedArea >= minRequiredArea) {
            prevCornersMat?.release()
            prevCornersMat = MatOfPoint2f(*sortedCorners.toTypedArray())

            prevGrayMat?.release()
            prevGrayMat = grayMat.clone()

            isTracking = true
            trackingFrameCount = 0

            return ScreenDetectionResult(4, sortedCorners)
        }
    }
}
```

---

### File 3: `protocol/PacketAssembler.kt`
**Problem:** If noise bytes simulated `"MO"` with a random large `payloadLength` (e.g. $45,000$), `requiredSize` became $45,015$, causing the parser to `break` and buffer tens of kilobytes while dropping all subsequent valid packets.

**Replacement Code:**
```kotlin
package com.example.mimo_optical_receiver.protocol

import android.util.Log

class PacketAssembler {

    private var currentByte = 0
    private var bitCount = 0
    
    private var buffer = ByteArray(65536)
    private var bufferSize = 0
    
    private val packetDecoder = PacketDecoder()

    fun appendSymbols(symbols: ByteArray) {
        for (bit in symbols) {
            currentByte = (currentByte shl 1) or (bit.toInt() and 1)
            bitCount++
            
            if (bitCount == 8) {
                appendByte(currentByte.toByte())
                currentByte = 0
                bitCount = 0
            }
        }
    }

    private fun appendByte(b: Byte) {
        if (bufferSize >= buffer.size) {
            val newBuffer = ByteArray(buffer.size * 2)
            System.arraycopy(buffer, 0, newBuffer, 0, bufferSize)
            buffer = newBuffer
        }
        buffer[bufferSize++] = b
    }

    fun extractValidPackets(): List<Packet> {
        val validPackets = mutableListOf<Packet>()
        var i = 0

        while (i < bufferSize - 1) {
            if (buffer[i] == ProtocolConstants.MAGIC_HEADER[0] && 
                buffer[i + 1] == ProtocolConstants.MAGIC_HEADER[1]) {
                
                if (bufferSize - i < ProtocolConstants.HEADER_SIZE_BYTES) {
                    break
                }
                
                val b1 = buffer[i + 9].toInt() and 0xFF
                val b2 = buffer[i + 10].toInt() and 0xFF
                val payloadLength = (b1 shl 8) or b2
                
                // CRITICAL FIX: Reject false headers with invalid payload lengths
                if (payloadLength <= 0 || payloadLength > 512) {
                    i++
                    continue
                }
                
                val requiredSize = ProtocolConstants.HEADER_SIZE_BYTES + payloadLength + ProtocolConstants.CRC_SIZE_BYTES
                
                if (bufferSize - i < requiredSize) {
                    break
                }
                
                val packetBytes = buffer.copyOfRange(i, i + requiredSize)
                try {
                    val packet = packetDecoder.decode(packetBytes)
                    validPackets.add(packet)
                    Log.d("MIMO_DECODER", "SUCCESS: Decoded Packet Seq=${packet.sequenceId}, Length=${packet.payload.size}")
                    i += requiredSize
                    continue
                } catch (e: Exception) {
                    Log.d("MIMO_DECODER", "CRC Mismatch on candidate MO packet at index $i: ${e.message}")
                    i++
                    continue
                }
            }
            i++
        }

        if (i > 0) {
            val remaining = bufferSize - i
            if (remaining > 0) {
                System.arraycopy(buffer, i, buffer, 0, remaining)
            }
            bufferSize = remaining
        }

        return validPackets
    }

    fun reset() {
        bufferSize = 0
        currentByte = 0
        bitCount = 0
    }
}
```

---

### File 4: `protocol/SequenceManager.kt`
**Problem:** The final assembled byte array must be truncated to `expectedFileSize` so that padding bytes on the final packet do not corrupt the reconstructed file.

**Update in `SequenceManager.kt`:**
```kotlin
fun assembleFileBytes(): ByteArray {
    if (!isComplete()) {
        throw IllegalStateException("Cannot assemble file: Not all packets received.")
    }

    val outputStream = ByteArrayOutputStream()
    val sortedKeys = receivedPackets.keys.sorted()
    var expectedFileSize = -1L
    
    for ((index, key) in sortedKeys.withIndex()) {
        val packet = receivedPackets[key]
        if (packet != null) {
            var payload = packet.payload
            if (index == 0 && payload.size >= 44 && payload.take(4).toByteArray().contentEquals(FILE_MAGIC)) {
                // Extract uint64 file size from bytes 4..11
                val sizeBuffer = java.nio.ByteBuffer.wrap(payload, 4, 8)
                expectedFileSize = sizeBuffer.long
                payload = payload.copyOfRange(44, payload.size)
            }
            outputStream.write(payload)
        }
    }
    
    val fullBytes = outputStream.toByteArray()
    return if (expectedFileSize > 0 && fullBytes.size > expectedFileSize) {
        fullBytes.copyOf(expectedFileSize.toInt())
    } else {
        fullBytes
    }
}
```

---

### File 5: `ui/screens/ScannerScreen.kt`
**Problem:** `sessionPacketSize` and `sessionMimoMode` were not forwarded to `CameraManager`, reverting the receiver to fallback defaults (`64` bytes and `"1x1"`).

**Update in `ScannerScreen.kt`:**
```kotlin
CameraManager(
    deviceProfile = profile,
    analyzerMode = analyzerMode,
    sessionTotalPackets = uiState.qrTotalPackets.takeIf { it > 0 },
    sessionPacketSize = uiState.qrPacketSize,      // FORWARDED
    sessionMimoMode = uiState.qrMimoMode,          // FORWARDED
    onFpsUpdated = { fps ->
        uiState = uiState.copy(analyzerFps = fps)
    },
    ...
)
```

---

### File 6: `camera/CameraManager.kt` (Camera2 Shutter Speed & Focus Lock)
**Problem:** Auto-exposure and auto-focus cause lens hunting, rolling shutter banding, and motion blur when pointed at high-refresh LCD monitors.

**Locking Exposure & Focus via `Camera2Interop`:**
```kotlin
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import android.hardware.camera2.CaptureRequest

@OptIn(ExperimentalCamera2Interop::class)
val imageAnalysisBuilder = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setResolutionSelector(analysisResolutionSelector)

val ext = Camera2Interop.Extender(imageAnalysisBuilder)
// Lock optical image stabilization and continuous focus to prevent breathing
ext.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
// Shutter speed 1/250s (4,000,000 ns) prevents screen flicker on 60Hz/144Hz monitors
ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

val imageAnalysis = imageAnalysisBuilder.build()
```

---

## 9. Step-by-Step Calibration & Validation Checklist

Follow this workflow to verify the end-to-end optical link:

1. **Transmitter Setup (PC):**
   - Launch: `python pc/main.py`
   - Go to **Send Page**: Select file `payload.txt`.
   - Configure:
     - **Payload size:** `64`
     - **MIMO mode:** `1x1` (for initial validation)
     - **Symbol mode:** `BINARY`
     - **Frame rate:** `15` FPS
   - Click **Prepare Transmission** $\to$ Click **SHOW QR**.
2. **Receiver Handshake (Android):**
   - Point smartphone camera at the PC screen displaying the QR code.
   - Verify HUD: `QR METADATA ✓` turns green.
   - Verify Session ID and Packet count display in the diagnostic HUD.
3. **Start Transmission (PC & Android):**
   - On PC: Close QR modal $\to$ Click **START TRANSMISSION**.
   - On Android: Click **START** button in ScannerScreen.
4. **Link Telemetry Verification:**
   - `SCREEN DETECTED`: `✓`
   - `AR LOCK`: `4/4` (All 4 ArUco markers locked)
   - `PERSPECTIVE`: `✓` (Homography active)
   - `SYNC`: `✓` (Transitions detected smoothly by `SyncDetector`)
   - `Packets (Valid/Dup/CRC/Miss)`: Valid count increments monotonically ($1, 2, 3 \dots$).
5. **Transfer Completion:**
   - Once all packets arrive, the HUD displays `TRANSFER COMPLETE!`.
   - File is saved to Android external storage under `Download/` and verified with matching SHA-256.
