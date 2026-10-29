package com.projectego.app.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The day key is an identifier, not text a person reads.
 *
 * Before SEC-05 it was formatted with the device locale: a Thai phone wrote the
 * Buddhist year and an Arabic one wrote localized digits, so changing the
 * phone's language renamed every stored day and the next rollover judged a
 * "yesterday" that had never existed.
 */
class DayKeyTest {

    @Test
    fun `the stored day never follows the phone's calendar or digits`() {
        val original = Locale.getDefault()
        try {
            val millis = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli()
            val expected = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()

            listOf("th-TH", "ar-SA", "en-US", "id-ID").forEach { tag ->
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals("locale $tag", expected, LockStateManager.dayKeyOf(millis))
            }
        } finally {
            Locale.setDefault(original)
        }
    }
}
