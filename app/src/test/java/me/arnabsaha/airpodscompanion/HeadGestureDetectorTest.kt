package me.arnabsaha.airpodscompanion

import me.arnabsaha.airpodscompanion.utils.HeadGestureDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HeadGestureDetectorTest {

    private lateinit var detector: HeadGestureDetector

    @Before
    fun setup() {
        detector = HeadGestureDetector()
    }

    @Test
    fun `initial state is NONE`() {
        assertEquals(HeadGestureDetector.Gesture.NONE, detector.lastGesture.value)
    }

    @Test
    fun `reset clears state back to NONE`() {
        detector.reset()
        assertEquals(HeadGestureDetector.Gesture.NONE, detector.lastGesture.value)
    }

    @Test
    fun `ignores packets shorter than 55 bytes`() {
        detector.processPacket(ByteArray(30))
        assertEquals(HeadGestureDetector.Gesture.NONE, detector.lastGesture.value)
    }

    @Test
    fun `processes calibration samples without gesture`() {
        // First 10 packets are calibration — no gesture should be detected
        repeat(10) {
            detector.processPacket(createTrackingPacket(0, 0, 0))
        }
        assertEquals(HeadGestureDetector.Gesture.NONE, detector.lastGesture.value)
    }

    @Test
    fun `no gesture for stationary head after calibration`() {
        // Calibrate
        repeat(10) { detector.processPacket(createTrackingPacket(0, 0, 0)) }
        // Stationary
        repeat(20) { detector.processPacket(createTrackingPacket(0, 0, 0)) }
        assertEquals(HeadGestureDetector.Gesture.NONE, detector.lastGesture.value)
    }

    /**
     * Create a minimal head tracking packet with orientation values at bytes 43-48.
     */
    private fun createTrackingPacket(o1: Int, o2: Int, o3: Int): ByteArray {
        val packet = ByteArray(60)
        // Write o1 at bytes 43-44 (little-endian)
        packet[43] = (o1 and 0xFF).toByte()
        packet[44] = ((o1 shr 8) and 0xFF).toByte()
        // Write o2 at bytes 45-46
        packet[45] = (o2 and 0xFF).toByte()
        packet[46] = ((o2 shr 8) and 0xFF).toByte()
        // Write o3 at bytes 47-48
        packet[47] = (o3 and 0xFF).toByte()
        packet[48] = ((o3 shr 8) and 0xFF).toByte()
        return packet
    }
}
