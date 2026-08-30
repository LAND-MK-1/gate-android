package com.gateai.sdk.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuotaAnchorDayTest {

    @Test
    fun `valid days pass through`() {
        assertEquals(1, QuotaAnchorDay.sanitize(1))
        assertEquals(15, QuotaAnchorDay.sanitize(15))
        // 29-31 are valid; the server clamps to short months
        assertEquals(29, QuotaAnchorDay.sanitize(29))
        assertEquals(31, QuotaAnchorDay.sanitize(31))
    }

    @Test
    fun `invalid days are dropped`() {
        assertNull(QuotaAnchorDay.sanitize(0))
        assertNull(QuotaAnchorDay.sanitize(32))
        assertNull(QuotaAnchorDay.sanitize(-5))
        assertNull(QuotaAnchorDay.sanitize(100))
    }

    @Test
    fun `null stays null`() {
        assertNull(QuotaAnchorDay.sanitize(null))
    }
}
