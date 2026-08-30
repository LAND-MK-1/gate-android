package com.gateai.sdk.network

import com.gateai.sdk.util.Iso8601
import java.util.Date

/**
 * Remaining device quota, parsed from the `X-Quota-*` response headers.
 *
 * When a gate has device usage limits configured, every successful proxied response
 * carries `X-Quota-Requests-Remaining`, `X-Quota-Tokens-Remaining`, and `X-Quota-Reset`
 * headers describing the tightest configured window. Read this from
 * [RawResponse.quotaStatus] to render limit UI (e.g., "N requests left") without
 * parsing headers yourself.
 *
 * @property requestsRemaining Requests remaining in the tightest window, if reported
 * @property tokensRemaining Tokens remaining in the tightest window, if reported
 * @property resetsAt When the tightest window resets (for rolling windows, when the
 *   oldest counted day ages out), if reported
 */
data class QuotaStatus(
    val requestsRemaining: Int?,
    val tokensRemaining: Int?,
    val resetsAt: Date?
) {
    companion object {
        private const val HEADER_REQUESTS_REMAINING = "x-quota-requests-remaining"
        private const val HEADER_TOKENS_REMAINING = "x-quota-tokens-remaining"
        private const val HEADER_RESET = "x-quota-reset"

        /**
         * Parses quota status from response headers (case-insensitively).
         *
         * @param headers The response headers
         * @return A [QuotaStatus] if at least one `X-Quota-*` header is present, null otherwise
         */
        fun fromHeaders(headers: Map<String, String>): QuotaStatus? {
            val lowercased = HashMap<String, String>(headers.size)
            for ((key, value) in headers) {
                lowercased[key.lowercase()] = value
            }

            val requests = lowercased[HEADER_REQUESTS_REMAINING]
            val tokens = lowercased[HEADER_TOKENS_REMAINING]
            val reset = lowercased[HEADER_RESET]

            if (requests == null && tokens == null && reset == null) return null

            return QuotaStatus(
                requestsRemaining = requests?.trim()?.toIntOrNull(),
                tokensRemaining = tokens?.trim()?.toIntOrNull(),
                resetsAt = Iso8601.parse(reset)
            )
        }
    }
}

/**
 * Validation for the quota anchor day (day-of-month 1-31).
 */
internal object QuotaAnchorDay {
    val VALID_RANGE = 1..31

    /** Returns the day if it is a valid day-of-month (1-31), null otherwise. */
    fun sanitize(day: Int?): Int? = day?.takeIf { it in VALID_RANGE }
}
