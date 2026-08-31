package com.gateai.sdk.network

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QuotaStatusTest {

    @Test
    fun `parses all six headers`() {
        val status = QuotaStatus.fromHeaders(
            mapOf(
                "X-Quota-Requests-Remaining" to "42",
                "X-Quota-Tokens-Remaining" to "125000",
                "X-Quota-Requests-Reset" to "2026-09-01T00:00:00.000Z",
                "X-Quota-Tokens-Reset" to "2026-09-02T00:00:00.000Z",
                "X-Quota-Requests-Limit" to "100",
                "X-Quota-Tokens-Limit" to "500000"
            )
        )

        assertNotNull(status)
        assertEquals(42, status.requestsRemaining)
        assertEquals(125000, status.tokensRemaining)
        assertEquals(Date(1788220800000L), status.requestsResetAt) // 2026-09-01T00:00:00Z
        assertEquals(Date(1788307200000L), status.tokensResetAt) // 2026-09-02T00:00:00Z
        assertEquals(100, status.requestsLimit)
        assertEquals(500000, status.tokensLimit)
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
    fun `limit header alone yields a status`() {
        val status = QuotaStatus.fromHeaders(
            mapOf("X-Quota-Requests-Limit" to "50")
        )

        assertNotNull(status)
        assertEquals(50, status.requestsLimit)
        assertNull(status.requestsRemaining)
        assertNull(status.tokensLimit)
    }

    @Test
    fun `parses limit headers case-insensitively`() {
        val status = QuotaStatus.fromHeaders(
            mapOf(
                "x-quota-requests-limit" to "10",
                "X-QUOTA-TOKENS-LIMIT" to "20"
            )
        )

        assertNotNull(status)
        assertEquals(10, status.requestsLimit)
        assertEquals(20, status.tokensLimit)
    }

    @Test
    fun `used and fraction computed from limit and remaining`() {
        val status = QuotaStatus.fromHeaders(
            mapOf(
                "X-Quota-Requests-Remaining" to "1",
                "X-Quota-Requests-Limit" to "4",
                "X-Quota-Tokens-Remaining" to "125000",
                "X-Quota-Tokens-Limit" to "500000"
            )
        )

        assertNotNull(status)
        assertEquals(3, status.requestsUsed)
        assertEquals(0.75, status.requestsUsedFraction)
        assertEquals(375000, status.tokensUsed)
        assertEquals(0.75, status.tokensUsedFraction)
    }

    @Test
    fun `used floors at zero when remaining exceeds limit`() {
        val status = QuotaStatus(
            requestsRemaining = 10,
            tokensRemaining = null,
            requestsResetAt = null,
            tokensResetAt = null,
            requestsLimit = 4,
            tokensLimit = null
        )

        assertEquals(0, status.requestsUsed)
        assertEquals(0.0, status.requestsUsedFraction)
    }

    @Test
    fun `used and fraction null when limits absent`() {
        val status = QuotaStatus.fromHeaders(
            mapOf(
                "X-Quota-Requests-Remaining" to "42",
                "X-Quota-Tokens-Remaining" to "125000"
            )
        )

        assertNotNull(status)
        assertNull(status.requestsUsed)
        assertNull(status.requestsUsedFraction)
        assertNull(status.tokensUsed)
        assertNull(status.tokensUsedFraction)
    }

    @Test
    fun `used null when remaining absent`() {
        val status = QuotaStatus.fromHeaders(
            mapOf("X-Quota-Tokens-Limit" to "500000")
        )

        assertNotNull(status)
        assertNull(status.tokensUsed)
        assertNull(status.tokensUsedFraction)
    }

    @Test
    fun `fraction null when limit is zero`() {
        val status = QuotaStatus(
            requestsRemaining = 0,
            tokensRemaining = null,
            requestsResetAt = null,
            tokensResetAt = null,
            requestsLimit = 0,
            tokensLimit = null
        )

        assertEquals(0, status.requestsUsed)
        assertNull(status.requestsUsedFraction)
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
