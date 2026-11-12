package com.example.mimo_optical_receiver.camera

import org.opencv.aruco.Aruco
import org.opencv.aruco.DetectorParameters
import org.opencv.aruco.Dictionary
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

data class ScreenDetectionResult(
    val markerCount: Int,
    val screenCorners: List<Point>,
    val isObstructed: Boolean = false,
    val obstructionReason: String? = null
)

class ScreenDetector {
    private val arucoDict: Dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_4X4_50)
    
    private var prevGrayMat: Mat? = null
    private var prevCornersMat: MatOfPoint2f? = null
    private var isTracking = false
    private var trackingFrameCount = 0

    fun detect(grayMat: Mat): ScreenDetectionResult {
        val totalFrameArea = grayMat.cols().toDouble() * grayMat.rows().toDouble()
        val minRequiredArea = totalFrameArea * 0.10 // 10% minimum frame area

        // 1. OPTICAL FLOW TRACKING (60 FPS Fast Path)
        if (isTracking && prevGrayMat != null && prevCornersMat != null) {
            trackingFrameCount++
            
            // Re-validate against ArUco / Contours every 5 frames (~100ms) to prevent hand-tremor drift
            if (trackingFrameCount > 5) {
                resetTracking()
            } else {
                val nextCornersMat = MatOfPoint2f()
                val status = MatOfByte()
                val err = MatOfFloat()

                try {
                    val winSize = Size(31.0, 31.0)
                    val maxLevel = 3
                    val criteria = TermCriteria(TermCriteria.EPS or TermCriteria.COUNT, 20, 0.03)

                    Video.calcOpticalFlowPyrLK(
                        prevGrayMat!!,
                        grayMat,
                        prevCornersMat!!,
                        nextCornersMat,
                        status,
                        err,
                        winSize,
                        maxLevel,
                        criteria
                    )

                    val statusArray = status.toArray()
                    val allTracked = statusArray.size == 4 && statusArray.all { it.toInt() == 1 }

                    if (allTracked) {
                        val trackedPoints = nextCornersMat.toList()
                        val currentArea = calculatePolygonArea(trackedPoints)

                        // GUARDRAIL: If tracked box shrinks below 10% frame area, force reset
                        if (currentArea >= minRequiredArea) {
                            prevCornersMat?.release()
                            prevCornersMat = nextCornersMat

                            prevGrayMat?.release()
                            prevGrayMat = grayMat.clone()

                            return ScreenDetectionResult(4, trackedPoints)
                        } else {
                            resetTracking()
                        }
                    } else {
                        resetTracking()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    resetTracking()
                } finally {
                    status.release()
                    err.release()
                }
            }
        }

        // 2. PRIMARY STRATEGY: ARUCO ANCHOR DETECTION (IDs 0, 1, 2, 3 with Expanded Window)
        var ids: Mat? = null
        val corners = ArrayList<Mat>()

        try {
            ids = Mat()

            val parameters = DetectorParameters.create().apply {
                set_adaptiveThreshWinSizeMin(3)
                set_adaptiveThreshWinSizeMax(55)
                set_adaptiveThreshWinSizeStep(8)
                set_adaptiveThreshConstant(7.0)
                set_polygonalApproxAccuracyRate(0.05)
            }

            Aruco.detectMarkers(grayMat, arucoDict, corners, ids, parameters)

            if (ids.rows() >= 3) {
                val detectedMarkers = mutableMapOf<Int, Mat>()
                for (i in 0 until ids.rows()) {
                    val markerId = ids.get(i, 0)[0].toInt()
                    if (markerId in 0..3) {
                        detectedMarkers[markerId] = corners[i]
                    }
                }

                fun getCenterAndCorners(mat: Mat): Pair<Point, List<Point>> {
                    val pts = FloatArray(8)
                    mat.get(0, 0, pts)
                    val cx = (pts[0] + pts[2] + pts[4] + pts[6]) / 4.0
                    val cy = (pts[1] + pts[3] + pts[5] + pts[7]) / 4.0
                    val cornerList = listOf(
                        Point(pts[0].toDouble(), pts[1].toDouble()),
                        Point(pts[2].toDouble(), pts[3].toDouble()),
                        Point(pts[4].toDouble(), pts[5].toDouble()),
                        Point(pts[6].toDouble(), pts[7].toDouble())
                    )
                    return Pair(Point(cx, cy), cornerList)
                }

                val parsedMarkers = mutableMapOf<Int, Pair<Point, List<Point>>>()
                for ((id, mat) in detectedMarkers) {
                    parsedMarkers[id] = getCenterAndCorners(mat)
                }

                if (parsedMarkers.size >= 3) {
                    // INNER CORNERS: Isolate pure 608x608 Central Data Area (0% ArUco marker pixel bleeding)
                    // Marker corner indexing in OpenCV: 0 = TL, 1 = TR, 2 = BR, 3 = BL
                    var dataTL: Point? = parsedMarkers[0]?.second?.get(2) // Marker 0 (TL) -> Inner BR corner
                    var dataTR: Point? = parsedMarkers[1]?.second?.get(3) // Marker 1 (TR) -> Inner BL corner
                    var dataBR: Point? = parsedMarkers[2]?.second?.get(0) // Marker 2 (BR) -> Inner TL corner
                    var dataBL: Point? = parsedMarkers[3]?.second?.get(1) // Marker 3 (BL) -> Inner TR corner

                    // GLARE RECOVERY: Reconstruct missing 4th inner corner geometrically if 3 are seen
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
            } else if (ids.rows() in 1..2) {
                val detected = ids.rows()
                return ScreenDetectionResult(
                    markerCount = detected,
                    screenCorners = emptyList(),
                    isObstructed = true,
                    obstructionReason = "Screen obstructed: Only $detected/4 corner anchors visible. Clear camera view."
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ids?.release()
            for (mat in corners) {
                mat.release()
            }
            corners.clear()
        }

        // 3. SECONDARY STRATEGY: OUTER CONTOUR WITH SYMMETRY SANITIZATION
        val binaryMat = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()

        try {
            Imgproc.threshold(grayMat, binaryMat, 160.0, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.findContours(
                binaryMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            var bestCorners: List<Point>? = null
            var maxArea = 0.0

            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val area = Imgproc.contourArea(contour2f)

                if (area >= minRequiredArea && area > maxArea) {
                    val peri = Imgproc.arcLength(contour2f, true)
                    val approx2f = MatOfPoint2f()
                    Imgproc.approxPolyDP(contour2f, approx2f, 0.02 * peri, true)

                    val approxPoints = approx2f.toList()
                    val approxMatOfPoint = MatOfPoint(*approxPoints.toTypedArray())

                    if (approxPoints.size == 4 && Imgproc.isContourConvex(approxMatOfPoint)) {
                        maxArea = area
                        
                        val tl = approxPoints.minByOrNull { it.x + it.y } ?: approxPoints[0]
                        val br = approxPoints.maxByOrNull { it.x + it.y } ?: approxPoints[0]
                        var tr = approxPoints.maxByOrNull { it.x - it.y } ?: approxPoints[0]
                        var bl = approxPoints.minByOrNull { it.x - it.y } ?: approxPoints[0]
                        
                        // SYMMETRY SANITIZATION: Prevent TR or BL corner from pulling inward when data cells flash white
                        val topDist = distance(tl, tr)
                        val botDist = distance(bl, br)

                        if (topDist < botDist * 0.82) {
                            tr = Point(tl.x + (br.x - bl.x), tl.y + (br.y - bl.y))
                        }

                        bestCorners = listOf(tl, tr, br, bl)
                    }
                    
                    approx2f.release()
                    approxMatOfPoint.release()
                }
                contour2f.release()
            }

            if (bestCorners != null) {
                prevCornersMat?.release()
                prevCornersMat = MatOfPoint2f(*bestCorners.toTypedArray())

                prevGrayMat?.release()
                prevGrayMat = grayMat.clone()

                isTracking = true
                trackingFrameCount = 0

                return ScreenDetectionResult(4, bestCorners)
            }

            return ScreenDetectionResult(0, emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            return ScreenDetectionResult(0, emptyList())
        } finally {
            binaryMat.release()
            hierarchy.release()
            for (c in contours) {
                c.release()
            }
            contours.clear()
        }
    }

    private fun distance(p1: Point, p2: Point): Double {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
    }

    private fun calculatePolygonArea(corners: List<Point>): Double {
        if (corners.size < 3) return 0.0
        val matOfPoint = MatOfPoint2f(*corners.toTypedArray())
        val area = Imgproc.contourArea(matOfPoint)
        matOfPoint.release()
        return area
    }

    fun resetTracking() {
        isTracking = false
        trackingFrameCount = 0
        prevGrayMat?.release()
        prevGrayMat = null
        prevCornersMat?.release()
        prevCornersMat = null
    }
}
