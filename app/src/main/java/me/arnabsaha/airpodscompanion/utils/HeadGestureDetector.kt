package me.arnabsaha.airpodscompanion.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedList

/**
 * Detects head gestures (nod = "yes", shake = "no") from AirPods head tracking data.
 *
 * Based on LibrePods' gesture detection algorithm:
 * - Orientation values from bytes 43-48 of head tracking packets
 * - Pitch (vertical nod): (o2 + o3) / 2
 * - Yaw (horizontal shake): (o2 - o3) / 2
 * - Detects peaks/troughs in the signal to identify rhythmic motion
 *
 * Confidence scoring:
 * - Amplitude (40%): Movement magnitude vs threshold
 * - Rhythm (20%): Consistency of peak timing
 * - Alternation (20%): Signal alternates positive/negative
 * - Isolation (20%): Target axis dominates non-target axis
 */
class HeadGestureDetector {

    companion object {
        private const val TAG = "HeadGesture"
        private const val CALIBRATION_SAMPLES = 10
        private const val BUFFER_SIZE = 30
        private const val PEAK_THRESHOLD = 800
        private const val DIRECTION_CHANGE_THRESHOLD = 300
        private const val MIN_EXTREMES_FAST = 5
        private const val MIN_EXTREMES_NORMAL = 4
        private const val CONFIDENCE_THRESHOLD = 0.80f
        private const val GESTURE_COOLDOWN_MS = 5000L
        private const val ORIENTATION_OFFSET = 5500
    }

    enum class Gesture { NONE, NOD_YES, SHAKE_NO }

    private val _lastGesture = MutableStateFlow(Gesture.NONE)
    val lastGesture: StateFlow<Gesture> = _lastGesture.asStateFlow()

    // Calibration
    private var calibrating = true
    private var calibrationCount = 0
    private var baselineHoriz = 0f
    private var baselineVert = 0f
    private var calibSumH = 0f
    private var calibSumV = 0f

    // Buffers
    private val horizBuffer = LinkedList<Int>()
    private val vertBuffer = LinkedList<Int>()
    private val horizPeaks = mutableListOf<Triple<Int, Int, Long>>()
    private val vertPeaks = mutableListOf<Triple<Int, Int, Long>>()
    private val peakIntervals = mutableListOf<Float>()

    // Direction tracking
    private var horizIncreasing: Boolean? = null
    private var vertIncreasing: Boolean? = null
    private var lastGestureTime = 0L

    /**
     * Process a head tracking packet (opcode 0x17).
     * Extract orientation from bytes 43-48 and detect gestures.
     */
    fun processPacket(rawBytes: ByteArray) {
        if (rawBytes.size < 50) return  // Need at least bytes up to offset 48

        // Extract orientation values (signed 16-bit, little-endian)
        val o1 = readInt16LE(rawBytes, 43)
        val o2 = readInt16LE(rawBytes, 45)
        val o3 = readInt16LE(rawBytes, 47)

        // Horizontal (yaw) and vertical (pitch)
        val horizontal = ((o2 + ORIENTATION_OFFSET) - (o3 + ORIENTATION_OFFSET)) / 2
        val vertical = ((o2 + ORIENTATION_OFFSET) + (o3 + ORIENTATION_OFFSET)) / 2

        if (calibrating) {
            calibSumH += horizontal
            calibSumV += vertical
            calibrationCount++
            if (calibrationCount >= CALIBRATION_SAMPLES) {
                baselineHoriz = calibSumH / CALIBRATION_SAMPLES
                baselineVert = calibSumV / CALIBRATION_SAMPLES
                calibrating = false
                Log.d(TAG, "Calibration complete: H=$baselineHoriz V=$baselineVert")
            }
            return
        }

        val normH = horizontal - baselineHoriz.toInt()
        val normV = vertical - baselineVert.toInt()

        horizBuffer.add(normH)
        vertBuffer.add(normV)
        if (horizBuffer.size > BUFFER_SIZE) horizBuffer.removeFirst()
        if (vertBuffer.size > BUFFER_SIZE) vertBuffer.removeFirst()

        detectPeaks()
        checkForGesture()
    }

    private fun detectPeaks() {
        if (horizBuffer.size < 4 || vertBuffer.size < 4) return

        val hValues = horizBuffer.takeLast(4)
        val current = hValues.last()
        val prev = hValues[hValues.size - 2]

        if (horizIncreasing == null) horizIncreasing = current > prev

        if (horizIncreasing == true && current < prev - DIRECTION_CHANGE_THRESHOLD) {
            if (kotlin.math.abs(prev) > PEAK_THRESHOLD) {
                horizPeaks.add(Triple(horizBuffer.size - 1, prev, System.currentTimeMillis()))
                trackPeakInterval()
            }
            horizIncreasing = false
        } else if (horizIncreasing == false && current > prev + DIRECTION_CHANGE_THRESHOLD) {
            if (kotlin.math.abs(prev) > PEAK_THRESHOLD) {
                horizPeaks.add(Triple(horizBuffer.size - 1, prev, System.currentTimeMillis()))
                trackPeakInterval()
            }
            horizIncreasing = true
        }

        // Same for vertical
        val vValues = vertBuffer.takeLast(4)
        val vCurrent = vValues.last()
        val vPrev = vValues[vValues.size - 2]

        if (vertIncreasing == null) vertIncreasing = vCurrent > vPrev

        if (vertIncreasing == true && vCurrent < vPrev - DIRECTION_CHANGE_THRESHOLD) {
            if (kotlin.math.abs(vPrev) > PEAK_THRESHOLD) {
                vertPeaks.add(Triple(vertBuffer.size - 1, vPrev, System.currentTimeMillis()))
                trackPeakInterval()
            }
            vertIncreasing = false
        } else if (vertIncreasing == false && vCurrent > vPrev + DIRECTION_CHANGE_THRESHOLD) {
            if (kotlin.math.abs(vPrev) > PEAK_THRESHOLD) {
                vertPeaks.add(Triple(vertBuffer.size - 1, vPrev, System.currentTimeMillis()))
                trackPeakInterval()
            }
            vertIncreasing = true
        }
    }

    private var lastPeakTime = 0L
    private fun trackPeakInterval() {
        val now = System.currentTimeMillis()
        if (lastPeakTime > 0) {
            peakIntervals.add((now - lastPeakTime) / 1000f)
            if (peakIntervals.size > 10) peakIntervals.removeAt(0)
        }
        lastPeakTime = now
    }

    private fun checkForGesture() {
        val now = System.currentTimeMillis()
        if (now - lastGestureTime < GESTURE_COOLDOWN_MS) return

        // Check horizontal (shake = NO)
        val recentHPeaks = horizPeaks.filter { now - it.third < 1500 }
        val recentVPeaks = vertPeaks.filter { now - it.third < 1500 }

        val minExtremes = if (peakIntervals.isNotEmpty() && peakIntervals.average() < 0.3)
            MIN_EXTREMES_FAST else MIN_EXTREMES_NORMAL

        if (recentHPeaks.size >= minExtremes) {
            val confidence = calculateConfidence(recentHPeaks, recentVPeaks, isHorizontal = true)
            if (confidence >= CONFIDENCE_THRESHOLD) {
                Log.d(TAG, "SHAKE (NO) detected! confidence=${"%.2f".format(confidence)}")
                _lastGesture.value = Gesture.SHAKE_NO
                lastGestureTime = now
                clearPeaks()
                return
            }
        }

        if (recentVPeaks.size >= minExtremes) {
            val confidence = calculateConfidence(recentVPeaks, recentHPeaks, isHorizontal = false)
            if (confidence >= CONFIDENCE_THRESHOLD) {
                Log.d(TAG, "NOD (YES) detected! confidence=${"%.2f".format(confidence)}")
                _lastGesture.value = Gesture.NOD_YES
                lastGestureTime = now
                clearPeaks()
                return
            }
        }
    }

    private fun calculateConfidence(
        targetPeaks: List<Triple<Int, Int, Long>>,
        otherPeaks: List<Triple<Int, Int, Long>>,
        isHorizontal: Boolean
    ): Float {
        if (targetPeaks.size < 2) return 0f

        // Amplitude factor (40%)
        val avgAmplitude = targetPeaks.map { kotlin.math.abs(it.second) }.average()
        val amplitudeFactor = (avgAmplitude / 600.0).coerceIn(0.0, 1.0).toFloat()

        // Rhythm factor (20%)
        val intervals = mutableListOf<Float>()
        for (i in 1 until targetPeaks.size) {
            intervals.add((targetPeaks[i].third - targetPeaks[i - 1].third) / 1000f)
        }
        val rhythmFactor = if (intervals.size >= 2) {
            val mean = intervals.average()
            val variance = intervals.map { (it - mean) * (it - mean) }.average()
            (1.0 - variance.coerceAtMost(1.0)).toFloat()
        } else 0.5f

        // Alternation factor (20%)
        var alternations = 0
        for (i in 1 until targetPeaks.size) {
            if ((targetPeaks[i].second > 0) != (targetPeaks[i - 1].second > 0)) alternations++
        }
        val alternationFactor = alternations.toFloat() / (targetPeaks.size - 1).coerceAtLeast(1)

        // Isolation factor (20%) - target axis should dominate
        val isolationFactor = if (otherPeaks.isEmpty()) 1f
            else (1f - otherPeaks.size.toFloat() / targetPeaks.size.toFloat()).coerceIn(0f, 1f)

        return amplitudeFactor * 0.4f + rhythmFactor * 0.2f +
               alternationFactor * 0.2f + isolationFactor * 0.2f
    }

    private fun clearPeaks() {
        horizPeaks.clear()
        vertPeaks.clear()
        peakIntervals.clear()
    }

    /**
     * Clear after a detected gesture — keeps calibration intact so the detector
     * doesn't immediately re-trigger on noise during recalibration.
     */
    fun clearAfterDetection() {
        horizBuffer.clear()
        vertBuffer.clear()
        clearPeaks()
        horizIncreasing = null
        vertIncreasing = null
        _lastGesture.value = Gesture.NONE
    }

    fun reset() {
        calibrating = true
        calibrationCount = 0
        calibSumH = 0f
        calibSumV = 0f
        horizBuffer.clear()
        vertBuffer.clear()
        clearPeaks()
        horizIncreasing = null
        vertIncreasing = null
        _lastGesture.value = Gesture.NONE
    }

    private fun readInt16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt()) shl 8)
    }
}
