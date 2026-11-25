package com.koala.air

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class NightTest {
    private fun at(h: Int, m: Int, dayOffset: Int = 0) = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 25, h, m, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_MONTH, dayOffset)
    }.timeInMillis

    @Test fun endTomorrowMorning() = assertEquals(at(7, 0, 1), Night.nextEnd(7 * 60, at(22, 30)))
    @Test fun endLaterToday() = assertEquals(at(23, 45), Night.nextEnd(23 * 60 + 45, at(22, 30)))
    @Test fun propsNeverUseClimSleep() {
        val p = Night.startProps(NightCfg())
        assertEquals("0", p["t_sleep"]); assertEquals("16", p["t_temp"]); assertEquals("5", p["t_fan_speed"]); assertEquals("1", p["t_power"])
    }
    @Test fun hhmm() = assertEquals("07:05", Night.hhmm(425))
}
