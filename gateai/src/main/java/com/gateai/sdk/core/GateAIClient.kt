package com.gateai.sdk.core

import android.content.Context
import com.gateai.sdk.auth.AuthApiClient
import com.gateai.sdk.logging.AndroidGateLogger
import com.gateai.sdk.logging.GateLogger
import com.gateai.sdk.network.GateApiException
import com.gateai.sdk.network.GateHttpClient
import com.gateai.sdk.playintegrity.PlayIntegrityManager
import com.gateai.sdk.security.DeviceKeyManager
import com.gateai.sdk.security.DeviceKeyMaterial
import com.gateai.sdk.util.SystemTimeProvider
import com.gateai.sdk.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class GateAIClient internal constructor(
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

    suspend fun authorizationHeaders(path: String, method: HttpMethod): Map<String, String> {
        val deviceKey = deviceKeyManager.ensureKey()
        val token = obtainToken(deviceKey)
        val dpop = DPoPBuilder.build(
            method = method,
            url = configuration.baseUrl.trimEnd('/') + "/$path",
            jwk = deviceKey.jwk,
            signer = { bytes -> Signer.sign(deviceKey.privateKey, bytes) }
        )

        return mapOf(
            "Authorization" to "Bearer ${token.accessToken}",
            "DPoP" to dpop
        )
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
                developmentToken = configuration.developmentToken
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

        fun create(
            context: Context,
            configuration: GateAIConfiguration,
            logger: GateLogger = AndroidGateLogger()
        ): GateAIClient {
            val deviceKeyManager = DeviceKeyManager.create(context)
        val httpClient = GateHttpClient(configuration, logger)
        val authApiClient = AuthApiClient(httpClient)
            val integrityManager = PlayIntegrityManager(context)
            val timeProvider = SystemTimeProvider()

            logger.setLogLevel(configuration.logLevel)

            return GateAIClient(
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
            "jwk" to jwk
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

