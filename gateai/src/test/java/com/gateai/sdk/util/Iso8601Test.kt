package com.gateai.sdk.util

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Iso8601Test {

    // 2026-09-01T00:00:00Z
    private val epochMillis = 1788220800000L

    @Test
    fun `parses JS toISOString format with milliseconds`() {
        assertEquals(Date(epochMillis), Iso8601.parse("2026-09-01T00:00:00.000Z"))
        assertEquals(Date(epochMillis + 123), Iso8601.parse("2026-09-01T00:00:00.123Z"))
    }

    @Test
    fun `parses plain Z timestamps without fractional seconds`() {
        assertEquals(Date(epochMillis), Iso8601.parse("2026-09-01T00:00:00Z"))
    }

    @Test
    fun `parses long fractional seconds by truncating to milliseconds`() {
        assertEquals(Date(epochMillis + 123), Iso8601.parse("2026-09-01T00:00:00.123456Z"))
    }

    @Test
    fun `parses short fractional seconds by padding`() {
        assertEquals(Date(epochMillis + 100), Iso8601.parse("2026-09-01T00:00:00.1Z"))
    }

    @Test
    fun `parses numeric UTC offsets`() {
        assertEquals(Date(epochMillis), Iso8601.parse("2026-09-01T02:00:00+02:00"))
        assertEquals(Date(epochMillis), Iso8601.parse("2026-08-31T19:00:00.000-05:00"))
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(Iso8601.parse(null))
        assertNull(Iso8601.parse(""))
        assertNull(Iso8601.parse("   "))
        assertNull(Iso8601.parse("tomorrow"))
        assertNull(Iso8601.parse("2026-09-01"))
    }
}
