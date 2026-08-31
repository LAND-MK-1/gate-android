package com.gateai.sdk.network

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RateLimitInfoTest {

    @Test
    fun `parses full device-window body`() {
        val info = RateLimitInfo.parse(
            """
            {
                "error": "rate_limited",
                "code": "device_monthly_requests_exceeded",
                "message": "Monthly request limit reached",
                "window": "monthly",
                "limit": 200,
                "used": 200,
                "resets_at": "2026-09-01T00:00:00.000Z"
            }
            """.trimIndent()
        )

        assertNotNull(info)
        assertEquals("device_monthly_requests_exceeded", info.code)
        assertEquals("Monthly request limit reached", info.message)
        assertEquals(RateLimitInfo.Window.Monthly, info.window)
        assertEquals(200, info.limit)
        assertEquals(200, info.used)
        assertEquals(Date(1788220800000L), info.resetsAt) // 2026-09-01T00:00:00Z
    }

    @Test
    fun `parses minimal non-window body`() {
        val info = RateLimitInfo.parse(
            """
            {
                "error": "rate_limited",
                "code": "global_rpm_exceeded",
                "message": "Too many requests"
            }
            """.trimIndent()
        )

        assertNotNull(info)
        assertEquals("global_rpm_exceeded", info.code)
        assertEquals("Too many requests", info.message)
        assertNull(info.window)
        assertNull(info.limit)
        assertNull(info.used)
        assertNull(info.resetsAt)
    }

    @Test
    fun `unknown window decodes safely preserving raw value`() {
        val info = RateLimitInfo.parse(
            """
            {
                "error": "rate_limited",
                "code": "device_weekly_requests_exceeded",
                "message": "Weekly limit reached",
                "window": "weekly"
            }
            """.trimIndent()
        )

        assertNotNull(info)
        assertEquals(RateLimitInfo.Window.Unknown("weekly"), info.window)
        assertEquals("weekly", info.window?.rawValue)
    }

    @Test
    fun `all known window values map correctly`() {
        assertEquals(RateLimitInfo.Window.Daily, RateLimitInfo.Window.fromRawValue("daily"))
        assertEquals(RateLimitInfo.Window.Monthly, RateLimitInfo.Window.fromRawValue("monthly"))
        assertEquals(RateLimitInfo.Window.Rolling30d, RateLimitInfo.Window.fromRawValue("rolling_30d"))
        assertEquals(RateLimitInfo.Window.Cycle, RateLimitInfo.Window.fromRawValue("cycle"))
        assertEquals("rolling_30d", RateLimitInfo.Window.Rolling30d.rawValue)
    }

    @Test
    fun `ignores unknown JSON keys`() {
        val info = RateLimitInfo.parse(
            """
            {
                "error": "rate_limited",
                "code": "device_daily_tokens_exceeded",
                "message": "Daily token limit reached",
                "window": "daily",
                "some_future_field": {"nested": true}
            }
            """.trimIndent()
        )

        assertNotNull(info)
        assertEquals(RateLimitInfo.Window.Daily, info.window)
    }

    @Test
    fun `returns null for non-rate-limited error body`() {
        assertNull(
            RateLimitInfo.parse("""{"error": "unauthorized", "code": "x", "message": "y"}""")
        )
    }

    @Test
    fun `returns null for provider-style 429 body`() {
        // e.g., an upstream provider 429 passed through the proxy
        assertNull(
            RateLimitInfo.parse("""{"error": {"message": "Rate limit reached", "code": "rate_limit_exceeded"}}""")
        )
    }

    @Test
    fun `returns null for missing code or message`() {
        assertNull(RateLimitInfo.parse("""{"error": "rate_limited", "message": "no code"}"""))
        assertNull(RateLimitInfo.parse("""{"error": "rate_limited", "code": "no_message"}"""))
    }

    @Test
    fun `returns null for malformed or empty bodies`() {
        assertNull(RateLimitInfo.parse(null))
        assertNull(RateLimitInfo.parse(""))
        assertNull(RateLimitInfo.parse("not json"))
        assertNull(RateLimitInfo.parse("<html>429 Too Many Requests</html>"))
    }

    @Test
    fun `exposed on GateApiException for 429 only`() {
        val body = """{"error": "rate_limited", "code": "device_cycle_tokens_exceeded", "message": "Cycle limit"}"""

        val on429 = GateApiException(429, emptyMap(), body)
        assertNotNull(on429.rateLimitInfo)
        assertEquals(RateLimitInfo.Window.Cycle, RateLimitInfo.Window.fromRawValue("cycle"))
        assertEquals("device_cycle_tokens_exceeded", on429.rateLimitInfo?.code)

        val on403 = GateApiException(403, emptyMap(), body)
        assertNull(on403.rateLimitInfo)
    }
}
