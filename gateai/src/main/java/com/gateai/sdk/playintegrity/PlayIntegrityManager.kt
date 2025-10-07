package com.gateai.sdk.playintegrity

import android.content.Context
import com.gateai.sdk.logging.GateLogger
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayIntegrityManager(
    context: Context,
    private val logger: GateLogger? = null
) {
    private val appContext = context.applicationContext
    private val manager = IntegrityManagerFactory.create(appContext)

    suspend fun requestIntegrityToken(nonce: String): String = withContext(Dispatchers.IO) {
        val request = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .build()

        runCatching {
            Tasks.await(manager.requestIntegrityToken(request)).token()
        }.onFailure { throwable ->
            logger?.error("Play Integrity request failed", throwable)
        }.getOrElse { throwable ->
            throw IntegrityException("Failed to obtain Play Integrity token", throwable)
        }
    }
}

class IntegrityException(message: String, cause: Throwable? = null) : Exception(message, cause)

