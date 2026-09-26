package io.github.jpsubway.app.core

import io.github.jpsubway.app.core.i18n.Choseong
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoseongTest {
    @Test
    fun choseongAndSubstring() {
        assertTrue(Choseong.matches("신주쿠", "ㅅㅈㅋ"))
        assertTrue(Choseong.matches("신주쿠산초메", "산초"))
        assertTrue(Choseong.matches("긴자", ""))
        assertFalse(Choseong.matches("시부야", "ㅅㅈ"))
    }
}
