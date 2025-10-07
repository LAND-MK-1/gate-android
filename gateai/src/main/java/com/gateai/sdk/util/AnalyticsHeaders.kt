package com.gateai.sdk.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * Helper to generate analytics headers for Gate/AI requests.
 *
 * These headers provide contextual information about the app and device for analytics purposes.
 */
internal class AnalyticsHeaders(
    private val context: Context,
    private val userStatus: String? = null
) {
    /**
     * Generates a map of analytics headers.
     *
     * @return A map with X-prefixed analytics headers
     */
    fun headers(): Map<String, String> {
        val headers = mutableMapOf<String, String>()

        // X-Client-Locale: Language and country code (e.g., "es-MX", "en-US")
        clientLocale()?.let { headers["X-Client-Locale"] = it }

        // X-App-Version: App version from package (e.g., "1.0.2")
        appVersion()?.let { headers["X-App-Version"] = it }

        // X-OS-Version: Android version (e.g., "14", "13")
        osVersion()?.let { headers["X-OS-Version"] = it }

        // X-User-Status: Custom status provided by developer
        userStatus?.let { headers["X-User-Status"] = it }

        // X-Device-Identifier: Android ID (unique per-app, per-device identifier)
        deviceIdentifier()?.let { headers["X-Device-Identifier"] = it }

        // X-Device-Type: Device model (e.g., "Pixel 8", "Samsung Galaxy S23")
        deviceType()?.let { headers["X-Device-Type"] = it }

        return headers
    }

    private fun clientLocale(): String? {
        return try {
            // Convert "en_US" to "en-US" format
            Locale.getDefault().toString().replace("_", "-")
        } catch (e: Exception) {
            null
        }
    }

    private fun appVersion(): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            null
        }
    }

    private fun osVersion(): String? {
        return try {
            Build.VERSION.RELEASE
        } catch (e: Exception) {
            null
        }
    }

    private fun deviceIdentifier(): String? {
        return try {
            // Android ID is a unique identifier per-app, per-device
            // It's reset on factory reset and stable across app reinstalls
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
    }

    private fun deviceType(): String? {
        return try {
            // Returns something like "Pixel 8" or "Samsung SM-G991U"
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            if (model.startsWith(manufacturer, ignoreCase = true)) {
                model.capitalize(Locale.ROOT)
            } else {
                "${manufacturer.capitalize(Locale.ROOT)} $model"
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize(locale: Locale): String {
        return this.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
    }
}

