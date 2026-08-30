package com.gateai.sdk.network

import com.gateai.sdk.util.Iso8601
import java.util.Date

/**
 * Remaining device quota, parsed from the `X-Quota-*` response headers.
 *
 * When a gate has device usage limits configured, every successful proxied response
 * carries `X-Quota-Requests-Remaining`, `X-Quota-Tokens-Remaining`,
 * `X-Quota-Requests-Reset`, and `X-Quota-Tokens-Reset` headers describing each
 * metric's binding window. Read this from [RawResponse.quotaStatus] to render
 * limit UI (e.g., "N requests left") without parsing headers yourself.
 *
 * @property requestsRemaining Requests remaining in the binding request window, if reported
 * @property tokensRemaining Tokens remaining in the binding token window, if reported
 * @property requestsResetAt When the binding request window resets (for rolling windows,
 *   when the oldest counted day ages out), if reported
 * @property tokensResetAt When the binding token window resets (for rolling windows,
 *   when the oldest counted day ages out), if reported
 */
data class QuotaStatus(
    val requestsRemaining: Int?,
    val tokensRemaining: Int?,
    val requestsResetAt: Date?,
    val tokensResetAt: Date?
) {
    companion object {
        private const val HEADER_REQUESTS_REMAINING = "x-quota-requests-remaining"
        private const val HEADER_TOKENS_REMAINING = "x-quota-tokens-remaining"
        private const val HEADER_REQUESTS_RESET = "x-quota-requests-reset"
        private const val HEADER_TOKENS_RESET = "x-quota-tokens-reset"

        /**
         * Parses quota status from response headers (case-insensitively).
         *
         * @param headers The response headers
         * @return A [QuotaStatus] if at least one of the four `X-Quota-*` headers is present,
         *   null otherwise
         */
        fun fromHeaders(headers: Map<String, String>): QuotaStatus? {
            val lowercased = HashMap<String, String>(headers.size)
            for ((key, value) in headers) {
                lowercased[key.lowercase()] = value
            }

            val requests = lowercased[HEADER_REQUESTS_REMAINING]
            val tokens = lowercased[HEADER_TOKENS_REMAINING]
            val requestsReset = lowercased[HEADER_REQUESTS_RESET]
            val tokensReset = lowercased[HEADER_TOKENS_RESET]

            if (requests == null && tokens == null && requestsReset == null && tokensReset == null) {
                return null
            }

            return QuotaStatus(
                requestsRemaining = requests?.trim()?.toIntOrNull(),
                tokensRemaining = tokens?.trim()?.toIntOrNull(),
                requestsResetAt = Iso8601.parse(requestsReset),
                tokensResetAt = Iso8601.parse(tokensReset)
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
