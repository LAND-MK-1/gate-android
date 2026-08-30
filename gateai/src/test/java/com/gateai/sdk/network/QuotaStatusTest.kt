package com.gateai.sdk.network

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QuotaStatusTest {

    @Test
    fun `parses all four headers`() {
        val status = QuotaStatus.fromHeaders(
            mapOf(
                "X-Quota-Requests-Remaining" to "42",
                "X-Quota-Tokens-Remaining" to "125000",
                "X-Quota-Requests-Reset" to "2026-09-01T00:00:00.000Z",
                "X-Quota-Tokens-Reset" to "2026-09-02T00:00:00.000Z"
            )
        )

        assertNotNull(status)
        assertEquals(42, status.requestsRemaining)
        assertEquals(125000, status.tokensRemaining)
        assertEquals(Date(1788220800000L), status.requestsResetAt) // 2026-09-01T00:00:00Z
        assertEquals(Date(1788307200000L), status.tokensResetAt) // 2026-09-02T00:00:00Z
    }

    @Test
    fun `parses headers case-insensitively`() {
        val status = QuotaStatus.fromHeaders(
            mapOf(
                "x-quota-requests-remaining" to "7",
                "X-QUOTA-TOKENS-REMAINING" to "99",
                "x-Quota-Requests-Reset" to "2026-09-01T00:00:00Z",
                "X-QUOTA-TOKENS-RESET" to "2026-09-02T00:00:00Z"
            )
        )

        assertNotNull(status)
        assertEquals(7, status.requestsRemaining)
        assertEquals(99, status.tokensRemaining)
        assertNotNull(status.requestsResetAt)
        assertNotNull(status.tokensResetAt)
    }

    @Test
    fun `returns null when no quota headers present`() {
        val status = QuotaStatus.fromHeaders(
            mapOf(
                "Content-Type" to "application/json",
                "X-Request-Id" to "abc123"
            )
        )

        assertNull(status)
    }

    @Test
    fun `old X-Quota-Reset header is not recognized`() {
        val status = QuotaStatus.fromHeaders(
            mapOf("X-Quota-Reset" to "2026-09-01T00:00:00Z")
        )

        assertNull(status)
    }

    @Test
    fun `returns partial status when only some headers present`() {
        val status = QuotaStatus.fromHeaders(
            mapOf("X-Quota-Requests-Remaining" to "3")
        )

        assertNotNull(status)
        assertEquals(3, status.requestsRemaining)
        assertNull(status.tokensRemaining)
        assertNull(status.requestsResetAt)
        assertNull(status.tokensResetAt)
    }

    @Test
    fun `single reset header alone yields a status`() {
        val status = QuotaStatus.fromHeaders(
            mapOf("X-Quota-Tokens-Reset" to "2026-09-02T00:00:00Z")
        )

        assertNotNull(status)
        assertNull(status.requestsRemaining)
        assertNull(status.tokensRemaining)
        assertNull(status.requestsResetAt)
        assertEquals(Date(1788307200000L), status.tokensResetAt)
    }

    @Test
    fun `non-numeric values become null fields`() {
        val status = QuotaStatus.fromHeaders(
            mapOf(
                "X-Quota-Requests-Remaining" to "many",
                "X-Quota-Requests-Reset" to "not-a-date"
            )
        )

        assertNotNull(status)
        assertNull(status.requestsRemaining)
        assertNull(status.requestsResetAt)
    }
}
