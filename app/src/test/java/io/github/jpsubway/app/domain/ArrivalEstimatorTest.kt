package io.github.jpsubway.app.domain

import io.github.jpsubway.app.domain.arrival.ArrivalEstimator
import io.github.jpsubway.app.domain.arrival.minutesLabel
import io.github.jpsubway.app.domain.model.ArrivalPhase
import io.github.jpsubway.app.domain.model.ArrivalSource
import io.github.jpsubway.app.domain.model.RealtimeSnapshot
import io.github.jpsubway.app.domain.model.StopTime
import io.github.jpsubway.app.domain.model.TrainPosition
import io.github.jpsubway.app.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalEstimatorTest {
    private val trip = Trip(
        "t1", "L", "A1", "asc", "D",
        stops = listOf(StopTime("A", 1000, 1000), StopTime("B", 1100, 1120), StopTime("C", 1220, 1240), StopTime("D", 1340, 1340)),
    )
    private val est = ArrivalEstimator()
    private val none = RealtimeSnapshot()

    @Test
    fun leftPreviousBySchedule() {
        val a = est.estimate(trip, "C", none, 1130)!!
        assertEquals(ArrivalPhase.LEFT_PREVIOUS, a.phase)
        assertEquals(2, a.minutes)
        assertEquals("2분 후", a.minutesLabel())
        assertEquals(ArrivalSource.SCHEDULE, a.source)
    }

    @Test
    fun atPreviousBySchedule() {
        assertEquals(ArrivalPhase.AT_PREVIOUS, est.estimate(trip, "C", none, 1110)!!.phase)
    }

    @Test
    fun approachingWithinOneMinute() {
        val a = est.estimate(trip, "C", none, 1180)!!
        assertEquals(ArrivalPhase.APPROACHING, a.phase)
        assertEquals("곧 도착", a.minutesLabel())
    }

    @Test
    fun realtimeDelayShiftsPrediction() {
        val rt = RealtimeSnapshot(
            trains = mapOf(RealtimeSnapshot.key("L", "A1") to TrainPosition("L", "A1", 120, "B", null)),
            available = true,
        )
        val a = est.estimate(trip, "C", rt, 1130)!!
        assertEquals(1340, a.predictedSec)
        assertEquals(4, a.minutes)
        assertEquals(ArrivalPhase.AT_PREVIOUS, a.phase)
        assertEquals(ArrivalSource.REALTIME, a.source)
    }

    @Test
    fun terminalAndDepartedExcluded() {
        assertNull(est.estimate(trip, "D", none, 1000))
        assertNull(est.estimate(trip, "B", none, 1300))
    }
}
