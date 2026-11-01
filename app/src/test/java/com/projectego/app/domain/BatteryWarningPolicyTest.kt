package com.projectego.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The battery warning: once, at fifteen percent, unplugged only.
 */
class BatteryWarningPolicyTest {

    @Test
    fun `fifteen percent and below warns while unplugged`() {
        assertTrue(BatteryWarningPolicy.shouldWarn(15, charging = false, alreadyWarned = false))
        assertTrue(BatteryWarningPolicy.shouldWarn(8, charging = false, alreadyWarned = false))
    }

    @Test
    fun `above fifteen percent says nothing`() {
        assertFalse(BatteryWarningPolicy.shouldWarn(16, charging = false, alreadyWarned = false))
        assertFalse(BatteryWarningPolicy.shouldWarn(80, charging = false, alreadyWarned = false))
    }

    @Test
    fun `charging is never a warning`() {
        assertFalse(BatteryWarningPolicy.shouldWarn(10, charging = true, alreadyWarned = false))
    }

    @Test
    fun `one warning per drop`() {
        assertFalse(BatteryWarningPolicy.shouldWarn(9, charging = false, alreadyWarned = true))
    }

    @Test
    fun `an unreadable level is not a warning`() {
        assertFalse(BatteryWarningPolicy.shouldWarn(-1, charging = false, alreadyWarned = false))
        assertFalse(BatteryWarningPolicy.shouldWarn(0, charging = false, alreadyWarned = false))
    }
}
