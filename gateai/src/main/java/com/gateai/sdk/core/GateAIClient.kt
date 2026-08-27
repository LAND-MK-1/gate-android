package com.gateai.sdk.core

import android.content.Context
import com.gateai.sdk.auth.AuthApiClient
import com.gateai.sdk.logging.AndroidGateLogger
import com.gateai.sdk.logging.GateLogger
import com.gateai.sdk.network.GateApiException
import com.gateai.sdk.network.GateHttpClient
import com.gateai.sdk.network.RawResponse
import com.gateai.sdk.playintegrity.PlayIntegrityManager
import com.gateai.sdk.security.DeviceKeyManager
import com.gateai.sdk.security.DeviceKeyMaterial
import com.gateai.sdk.util.AnalyticsHeaders
import com.gateai.sdk.util.SystemTimeProvider
import com.gateai.sdk.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * The main client for interacting with the Gate/AI authentication and proxy service.
 *
 * `GateAIClient` provides a complete solution for authenticating with Gate/AI and making
 * authenticated requests through the proxy. It handles the entire OAuth 2.0 + DPoP + Play Integrity
 * flow automatically, including:
 *
 * - Device key generation and management in Android Keystore (StrongBox when available)
 * - Play Integrity attestation (on device) or development token flow (emulator)
 * - Access token acquisition and automatic refresh
 * - DPoP proof generation for each request
 * - Nonce challenge handling with automatic retry
 * - Analytics headers (device info, app version, locale) on all requests
 *
 * ## Usage
 *
 * ```kotlin
 * // Initialize the client
 * val configuration = GateAIConfiguration(
 *     baseUrl = "https://yourteam.in.gate-ai.net",
 *     packageName = packageName,
 *     signingCertSha256 = "AA:BB:CC:...",
 *     logLevel = GateAIConfiguration.LogLevel.INFO
 * )
 * val client = GateAIClient.create(context, configuration)
 *
 * // Make authenticated requests
 * val response = client.performProxyRequest(
 *     path = "openai/chat/completions",
 *     method = HttpMethod.POST,
 *     body = requestBody.toByteArray(),
 *     additionalHeaders = mapOf("Content-Type" to "application/json")
 * )
 * ```
 *
 * ## Thread Safety
 *
 * `GateAIClient` is thread-safe and can be safely accessed from multiple concurrent coroutines.
 * The internal authentication session uses mutex synchronization for proper thread safety.
 */
class GateAIClient internal constructor(
    private val context: Context,
    private val configuration: GateAIConfiguration,
    private val deviceKeyManager: DeviceKeyManager,
    private val authApiClient: AuthApiClient,
    private val playIntegrityManager: PlayIntegrityManager,
    private val timeProvider: TimeProvider,
    private val logger: GateLogger,
    private val httpClient: GateHttpClient
) {
    private val tokenMutex = Mutex()
    private val authState = MutableStateFlow<TokenState>(TokenState.Empty)

    /**
     * Optional user status for analytics (e.g., "free", "premium", "trial").
     *
     * This value is included in the `X-User-Status` header on all authenticated requests.
     * Set this property to track different user segments or subscription tiers in your analytics.
     *
     * ## Example
     *
     * ```kotlin
     * client.userStatus = "premium"
     * // or
     * client.userStatus = "free-trial"
     * ```
     */
    var userStatus: String? = null

    /**
     * Generates authorization headers for a path relative to the configured base URL.
     *
     * This method automatically obtains a valid access token, generates the DPoP proof,
     * and includes analytics headers for the request.
     *
     * @param path The path relative to your base URL (e.g., "openai/chat/completions")
     * @param method The HTTP method to use
     * @param nonce Optional DPoP nonce from a previous 401 response for retry
     * @return A map containing Authorization, DPoP, and analytics headers
     * @throws GateApiException if authentication fails
     */
    suspend fun authorizationHeaders(
        path: String,
        method: HttpMethod,
        nonce: String? = null
    ): Map<String, String> {
        val url = configuration.baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        return authorizationHeadersForUrl(url, method, nonce)
    }

    private suspend fun authorizationHeadersForUrl(
        fullUrl: String,
        method: HttpMethod,
        nonce: String? = null
    ): Map<String, String> {
        val deviceKey = deviceKeyManager.ensureKey()
        val token = obtainToken(deviceKey)
        val dpop = DPoPBuilder.build(
            method = method,
            url = fullUrl,
            jwk = deviceKey.jwk,
            signer = { bytes -> Signer.sign(deviceKey.privateKey, bytes) },
            nonce = nonce
        )

        // Start with auth headers
        val headers = mutableMapOf(
            "Authorization" to "Bearer ${token.accessToken}",
            "DPoP" to dpop
        )

        // Add analytics headers
        val analyticsHeaders = AnalyticsHeaders(context, userStatus)
        headers.putAll(analyticsHeaders.headers())

        return headers
    }

    /**
     * Performs an authenticated proxy request to a path relative to the configured base URL.
     *
     * This is the recommended method for making authenticated requests through the Gate/AI proxy.
     * It automatically handles authentication, DPoP proof generation, and nonce challenges.
     *
     * @param path The path relative to your base URL (e.g., "openai/chat/completions")
     * @param method The HTTP method to use
     * @param body Optional request body data
     * @param additionalHeaders Additional headers to include in the request (e.g., "Content-Type")
     * @return A RawResponse containing status code, headers, and body
     * @throws GateApiException if authentication fails or the request fails
     *
     * ## Example
     *
     * ```kotlin
     * val requestBody = """
     * {
     *     "model": "gpt-4",
     *     "messages": [{"role": "user", "content": "Hello!"}]
     * }
     * """.trimIndent()
     *
     * val response = client.performProxyRequest(
     *     path = "openai/chat/completions",
     *     method = HttpMethod.POST,
     *     body = requestBody.toByteArray(),
     *     additionalHeaders = mapOf("Content-Type" to "application/json")
     * )
     *
     * if (response.statusCode == 200) {
     *     val result = String(response.body)
     *     // Process response
     * }
     * ```
     */
    suspend fun performProxyRequest(
        path: String,
        method: HttpMethod,
        body: ByteArray? = null,
        additionalHeaders: Map<String, String> = emptyMap()
    ): RawResponse {
        val url = configuration.baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        return executeProxyRequest(url, method, body, additionalHeaders)
    }

    /**
     * Gets the current access token if available.
     *
     * @return The current access token, or null if no valid token is cached
     */
    suspend fun currentAccessToken(): String? {
        val current = authState.first()
        return if (current is TokenState.Valid) {
            val now = timeProvider.currentTimeSeconds()
            if (current.expiresAt - TOKEN_EXPIRY_GRACE_SECONDS > now) {
                current.accessToken
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Clears the cached authentication state, forcing a fresh token to be minted on the next request.
     *
     * This is useful for testing or when you want to force re-authentication.
     */
    fun clearCachedState() {
        authState.value = TokenState.Empty
        logger.info("Cleared cached authentication state")
    }

    /**
     * Extracts the DPoP nonce from a 401 response.
     *
     * @param headers The response headers from a 401 response
     * @return The DPoP nonce if present, null otherwise
     */
    fun extractDPoPNonce(headers: Map<String, String>): String? {
        return headers["DPoP-Nonce"] ?: headers["dpop-nonce"]
    }

    private suspend fun executeProxyRequest(
        url: String,
        method: HttpMethod,
        body: ByteArray?,
        additionalHeaders: Map<String, String>
    ): RawResponse {
        logger.debug("Performing proxy request: $method $url")
        
        // Try initial request without nonce
        val initial = sendProxyRequest(url, method, body, additionalHeaders, nonce = null)
        
        // Handle DPoP nonce challenge
        if (initial.status == 401) {
            val nonce = extractDPoPNonce(initial.headers)
            if (nonce != null) {
                logger.info("Received DPoP-Nonce challenge, retrying request with nonce")
                return sendProxyRequest(url, method, body, additionalHeaders, nonce = nonce)
            }
        }
        
        return initial
    }

    private suspend fun sendProxyRequest(
        url: String,
        method: HttpMethod,
        body: ByteArray?,
        additionalHeaders: Map<String, String>,
        nonce: String?
    ): RawResponse {
        val authHeaders = authorizationHeadersForUrl(url, method, nonce)
        val allHeaders = additionalHeaders + authHeaders
        return httpClient.postRaw(method, url, body, allHeaders)
    }

    private suspend fun obtainToken(deviceKey: DeviceKeyMaterial): TokenState.Valid {
        val current = authState.first()
        val now = timeProvider.currentTimeSeconds()

        if (current is TokenState.Valid && current.expiresAt - TOKEN_EXPIRY_GRACE_SECONDS > now) {
            return current
        }

        return tokenMutex.withLock {
            val lockedCurrent = authState.value
            val lockedNow = timeProvider.currentTimeSeconds()

            if (lockedCurrent is TokenState.Valid && lockedCurrent.expiresAt - TOKEN_EXPIRY_GRACE_SECONDS > lockedNow) {
                return@withLock lockedCurrent
            }

            logger.info("Refreshing Gate/AI token")
            val challenge = authApiClient.fetchChallenge()
            val integrityToken = fetchIntegrityToken(challenge.nonce)
            val dpop = DPoPBuilder.build(
                method = HttpMethod.POST,
                url = configuration.baseUrl.trimEnd('/') + "/token",
                jwk = deviceKey.jwk,
                signer = { bytes -> Signer.sign(deviceKey.privateKey, bytes) }
            )

            val response = authApiClient.exchangeToken(
                configuration = configuration,
                deviceKeyJwk = deviceKey.jwk,
                integrityToken = integrityToken,
                dpopProof = dpop,
                developmentToken = configuration.developmentToken,
                nonce = challenge.nonce
            )

            val expiresAt = lockedNow + response.expiresInSeconds
            val valid = TokenState.Valid(
                accessToken = response.accessToken,
                expiresAt = expiresAt,
                mode = response.mode
            )
            authState.value = valid
            valid
        }
    }

    private suspend fun fetchIntegrityToken(nonce: String): String? {
        return if (configuration.developmentToken != null) {
            null
        } else {
            playIntegrityManager.requestIntegrityToken(nonce)
        }
    }

    companion object {
        private const val TOKEN_EXPIRY_GRACE_SECONDS = 60

        /**
         * Creates a new Gate/AI client with the specified configuration.
         *
         * @param context The application context
         * @param configuration The configuration containing your Gate/AI tenant URL and credentials
         * @param logger Optional custom logger implementation. Defaults to AndroidGateLogger
         * @return A configured GateAIClient instance
         */
        fun create(
            context: Context,
            configuration: GateAIConfiguration,
            logger: GateLogger = AndroidGateLogger()
        ): GateAIClient {
            val deviceKeyManager = DeviceKeyManager.create(context)
            val httpClient = GateHttpClient(configuration, logger)
            val authApiClient = AuthApiClient(httpClient)
            val integrityManager = PlayIntegrityManager(context, configuration.cloudProjectNumber)
            val timeProvider = SystemTimeProvider()

            logger.setLogLevel(configuration.logLevel)

            return GateAIClient(
                context = context.applicationContext,
                configuration = configuration,
                deviceKeyManager = deviceKeyManager,
                authApiClient = authApiClient,
                playIntegrityManager = integrityManager,
                timeProvider = timeProvider,
                logger = logger,
                httpClient = httpClient
            )
        }
    }
}

data class ProxyResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String?
)

sealed class TokenState {
    data object Empty : TokenState()

    data class Valid(
        val accessToken: String,
        val expiresAt: Long,
        val mode: String?
    ) : TokenState()
}

enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH")
}

internal object DPoPBuilder {
    suspend fun build(
        method: HttpMethod,
        url: String,
        jwk: com.gateai.sdk.core.DeviceKeyJwk,
        signer: suspend (ByteArray) -> ByteArray,
        nonce: String? = null,
        timeProvider: TimeProvider = SystemTimeProvider()
    ): String {
        val header = mapOf(
            "typ" to "dpop+jwt",
            "alg" to "ES256",
            // Plain map: the canonical JSON signer only serializes primitives/maps
            "jwk" to mapOf("kty" to jwk.kty, "crv" to jwk.crv, "x" to jwk.x, "y" to jwk.y)
        )

        val payload = buildMap {
            put("htu", url)
            put("htm", method.value)
            put("iat", timeProvider.currentTimeSeconds())
            put("jti", UUID.randomUUID().toString())
            nonce?.let { put("nonce", it) }
        }

        val signerImpl = com.gateai.sdk.util.JwtSigner(signer)
        return signerImpl.sign(header, payload)
    }
}

internal object Signer {
    suspend fun sign(privateKey: java.security.PrivateKey, data: ByteArray): ByteArray {
        return com.gateai.sdk.util.EcdsaSigner.sign(privateKey, data)
    }
}

