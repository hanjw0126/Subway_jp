package io.github.jpsubway.app.ui

import androidx.compose.ui.geometry.Offset
import io.github.jpsubway.app.ui.common.minutesText
import io.github.jpsubway.app.ui.common.trainTypeKo
import io.github.jpsubway.app.ui.map.offsetPolyline
import io.github.jpsubway.app.ui.theme.parseColor
import org.junit.Assert.assertEquals
import org.junit.Test

class UiHelpersTest {
    @Test fun trainTypes() {
        assertEquals("", trainTypeKo("odpt.TrainType:TokyoMetro.Local"))
        assertEquals("급행", trainTypeKo("odpt.TrainType:TokyoMetro.Express"))
        assertEquals("특급", trainTypeKo("odpt.TrainType:Odakyu.LimitedExpress"))
        assertEquals("준급", trainTypeKo("odpt.TrainType:Tobu.SemiExpress"))
    }

    @Test fun minutes() {
        assertEquals("1분", minutesText(1))
        assertEquals("2분", minutesText(61))
        assertEquals("0분", minutesText(-5))
    }

    @Test fun horizontalOffsetMovesPerpendicular() {
        val out = offsetPolyline(listOf(Offset(0f, 0f), Offset(10f, 0f)), 2f)
        assertEquals(2f, out[0].y, 1e-4f)
        assertEquals(2f, out[1].y, 1e-4f)
        assertEquals(10f, out[1].x, 1e-4f)
    }

    @Test fun colorParsing() {
        assertEquals(parseColor("#F39700"), parseColor("F39700"))
    }
}
