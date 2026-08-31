# Gate/AI Android SDK

Android SDK for Gate/AI authentication using Play Integrity, DPoP (RFC 9449), and hardware-backed device keys.

## Features

- 🔐 **Hardware-Backed Device Keys** - Android Keystore with StrongBox preference
- 🛡️ **Play Integrity Attestation** - Device verification for production apps
- 🔧 **Development Mode** - Bypass Play Integrity for testing/emulators
- 🔑 **DPoP Token Generation** - RFC 9449 compliant per-request proofs
- ♻️ **Automatic Token Refresh** - Transparent token lifecycle management
- 🔄 **DPoP Nonce Handling** - Automatic retry on 401 challenges
- 📊 **Analytics Headers** - Device info, app version, locale tracking
- 🧵 **Thread-Safe** - Coroutine-based with mutex synchronization

## Installation

### Gradle

The SDK is distributed through [JitPack](https://jitpack.io/#GateAI-net/gate-android). Add the
JitPack repository, then depend on the tagged release:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.GateAI-net:gate-android:v1.1.0")
}
```

## Quick Start

```kotlin
import com.gateai.sdk.core.*

// Initialize the client
val configuration = GateAIConfiguration(
    baseUrl = "https://yourteam.in.gate-ai.net",
    packageName = packageName,
    signingCertSha256 = "AA:BB:CC:DD:EE:FF:...", // Your app's SHA-256 fingerprint
    cloudProjectNumber = 123456789012L, // Google Cloud project number (see Play Integrity Setup)
    developmentToken = null, // Use for testing without Play Integrity
    logLevel = GateAIConfiguration.LogLevel.INFO
)

val client = GateAIClient.create(applicationContext, configuration)

// Make authenticated requests
lifecycleScope.launch {
    try {
        val requestBody = """
        {
            "model": "gpt-4",
            "messages": [{"role": "user", "content": "Hello!"}]
        }
        """.trimIndent()
        
        val response = client.performProxyRequest(
            path = "openai/chat/completions",
            method = HttpMethod.POST,
            body = requestBody.toByteArray(),
            additionalHeaders = mapOf("Content-Type" to "application/json")
        )
        
        if (response.status == 200) {
            val result = String(response.body)
            println(result)
        }
    } catch (e: Exception) {
        Log.e("GateAI", "Request failed", e)
    }
}
```

## Configuration

### Getting Your SHA-256 Fingerprint

```bash
# For debug keystore
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# For release keystore
keytool -list -v -keystore /path/to/your/release.keystore -alias your-alias
```

Look for the `SHA256:` line and copy the colon-delimited hex string.

### Development Mode (For Testing)

When testing on emulators or unsigned builds, use a development token to bypass Play Integrity:

```kotlin
val configuration = GateAIConfiguration(
    baseUrl = "https://yourteam.in.gate-ai.net",
    packageName = packageName,
    signingCertSha256 = "...",
    developmentToken = "your-dev-token-here", // Bypass Play Integrity
    logLevel = GateAIConfiguration.LogLevel.DEBUG
)
```

You can conditionally include it for debug builds:

```kotlin
developmentToken = if (BuildConfig.DEBUG) "dev-token" else null
```

## Play Integrity Setup (Production)

For production apps, Play Integrity requires proper setup in Google Cloud Console and Google Play Console.

### Prerequisites

1. **Google Play Console Account**
2. **Google Cloud Console Access**
3. **App Published or In Testing** (Internal, Closed, Open Alpha/Beta, or Production)

### Step 1: Link Your App in Google Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Select your app
3. Navigate to **Release** → **Setup** → **App Integrity**
4. Link a **Google Cloud project** (create one if needed) and note its **project number** — the numeric value shown on the Google Cloud Console "Project info" card
5. Set that number as `cloudProjectNumber` in your `GateAIConfiguration`. Always set it: Google requires it for any build not installed from the Play Store — which includes every development build you run from Android Studio or install with `adb` — and it is harmless for Play Store installs. Without it, development builds fail with Integrity error `-16` before any network call.

### Step 2: Enable Play Integrity API in Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Select the project associated with your app
3. Navigate to **APIs & Services** → **Library**
4. Search for **"Play Integrity API"**
5. Click **Enable**

### Step 3: Link App Signing Certificate

The Play Integrity API validates your app using:
- **Package Name** - Must match exactly
- **SHA-256 Certificate Fingerprint** - From your signing key

#### For Apps Using Play App Signing

If you're using Google Play App Signing (recommended):

1. In Play Console, go to **Release** → **Setup** → **App Signing**
2. Copy the **SHA-256 certificate fingerprint** under "App signing key certificate"
3. Use this fingerprint in your `GateAIConfiguration`

#### For Apps With Manual Signing

Use the SHA-256 fingerprint from your release keystore:

```bash
keytool -list -v -keystore your-release.keystore -alias your-alias
```

### Step 4: Grant Gate/AI permission to verify your app

Gate/AI verifies integrity tokens server-side by calling Google's `decodeIntegrityToken` API, which is authorized against **your** cloud project. You must grant Gate/AI's service account access — no keys or secrets are exchanged, and you can revoke the grant at any time:

1. In [Google Cloud Console](https://console.cloud.google.com), open the project linked in Step 1
2. Navigate to **IAM & Admin** → **IAM** → **Grant access**
3. Add this principal: `gateai-integrity-check@gateai-506813.iam.gserviceaccount.com`
4. Assign the role **Service Usage Consumer**
5. Save

Until this grant exists, attestation fails server-side even when your app obtains integrity tokens successfully.

### Step 5: Test Your Integration

1. **Build a signed APK/AAB** with your release key
2. **Upload to Play Console** (Internal Testing track is fine)
3. **Install from Play Store** for the fully Play-recognized path — or test a development build directly if your gate has "Require Play-recognized app" turned off in the Gate/AI Portal
4. **Run your app** and test authentication

### Step 6: Monitor for Errors

Enable debug logging to see detailed error messages:

```kotlin
logLevel = GateAIConfiguration.LogLevel.DEBUG
```

## Troubleshooting

### "Failed to obtain Play Integrity token"

This error occurs when the Play Integrity API cannot verify your app. The improved error message will now show you the specific cause:

#### Common Error Codes

**API_NOT_AVAILABLE**
- Device doesn't support Play Integrity (rare)
- Solution: Use `developmentToken` for testing on this device

**APP_NOT_INSTALLED**
- App wasn't installed from Google Play Store
- Solution: Install from Play Store or use `developmentToken` for testing

**PLAY_SERVICES_NOT_FOUND / PLAY_SERVICES_VERSION_OUTDATED**
- Google Play Services missing or outdated
- Solution: Update Google Play Services on the device

**GOOGLE_SERVER_UNAVAILABLE / NETWORK_ERROR**
- Network connectivity issue
- Solution: Check internet connection and retry

**PLAY_STORE_ACCOUNT_NOT_FOUND**
- No Google account signed in
- Solution: Sign in to a Google account on the device

**NO_ERROR or Generic Errors**
- Usually means app is not properly linked in Play Console
- Solution: 
  1. Verify package name matches exactly
  2. Verify SHA-256 certificate matches signing key
  3. Ensure app is published in at least Internal Testing
  4. Wait 24-48 hours after first upload for Google to process
  5. Use `developmentToken` for testing

### Package Name Mismatch

Ensure the package name in your `GateAIConfiguration` matches:
- Your `AndroidManifest.xml` → `<manifest package="...">`
- Your `build.gradle.kts` → `applicationId`
- Your Google Play Console listing

### Certificate Fingerprint Mismatch

Ensure you're using the correct certificate:
- **For Google Play App Signing**: Use the fingerprint from Play Console → App Signing
- **For Manual Signing**: Use the fingerprint from your actual release keystore
- **Don't use** debug keystore fingerprints for production

### "App not approved for Play Integrity yet"

After first upload to Play Console:
- Google needs to process and approve your app for Play Integrity
- This can take **24-48 hours**
- Use `developmentToken` for immediate testing

### Testing Without Play Store

For local development and testing:

```kotlin
// Add development token to configuration
developmentToken = "get-from-your-backend-admin"
```

This bypasses Play Integrity entirely. **Never** ship production apps with a development token.

## API Reference

### GateAIClient

#### `performProxyRequest()`

Makes an authenticated request through the Gate/AI proxy with automatic DPoP nonce retry.

```kotlin
suspend fun performProxyRequest(
    path: String,
    method: HttpMethod,
    body: ByteArray? = null,
    additionalHeaders: Map<String, String> = emptyMap()
): RawResponse
```

#### `authorizationHeaders()`

Generates authorization headers without making a request.

```kotlin
suspend fun authorizationHeaders(
    path: String,
    method: HttpMethod,
    nonce: String? = null
): Map<String, String>
```

#### `currentAccessToken()`

Gets the current access token if available.

```kotlin
suspend fun currentAccessToken(): String?
```

#### `clearCachedState()`

Forces a fresh token to be minted on the next request.

```kotlin
fun clearCachedState()
```

#### `userStatus` Property

Set user status for analytics tracking.

```kotlin
client.userStatus = "premium" // or "free", "trial", etc.
```

### Analytics Headers

The SDK includes these headers on all requests:

| Header | Set by | Description |
|---|---|---|
| `X-Client-Locale` | Automatic | User's language/region (e.g., "en-US") |
| `X-App-Version` | Automatic | App version from manifest |
| `X-OS-Version` | Automatic | Android OS version |
| `X-Device-Identifier` | Automatic | Android ID (per-app, per-device) |
| `X-Device-Type` | Automatic | Device manufacturer and model |
| `X-Device-Model` | Automatic | Raw hardware model identifier (e.g., "SM-G991U") |
| `X-Environment` | Automatic | `development` (debuggable build) or `production` |
| `X-SDK-Version` | Automatic | Gate/AI SDK version (e.g., "1.1.0") |
| `X-User-Status` | Developer (`client.userStatus`) | Custom user segment (e.g., "free", "premium") |
| `X-User-Tier` | Developer (`client.userTier`) | User's plan tier (e.g., "free", "pro"); matched exactly (case-sensitive) against Portal-configured per-tier usage limits |
| `X-User-Identifier` | Developer (`client.userIdentifier`) | Opaque user/account ID from your own system |
| `X-App-Feature` | Developer (`client.appFeature`) | Feature tag for cost attribution (e.g., "chat"); can be overridden per request via `additionalHeaders` |
| `X-Quota-Anchor-Day` | Developer (`client.quotaAnchorDay`) | Day-of-month (1-31) the user's subscription renews; anchors billing-cycle usage windows |

Developer-set headers are only sent when the corresponding property is set.

> **Note:** `X-User-Identifier` should be an opaque ID (e.g., a UUID or database key) —
> never an email address or name. Do not send PII in analytics headers.

## Usage limits & quotas

Gates can enforce per-device usage limits over daily, calendar-month, rolling
30-day, or user-billing-cycle windows (configured in the Gate/AI portal). The
SDK surfaces everything your app needs to render limit UI without parsing
headers or JSON yourself.

**Key per-tier limits off the user's plan** by setting `client.userTier`. Limits
configured per tier in the Gate/AI Portal match this value exactly
(case-sensitive):

```kotlin
client.userTier = "pro"
```

**Anchor the billing-cycle window** by telling the SDK which day of the month
the user's subscription renews (read it from Play Billing / RevenueCat):

```kotlin
client.quotaAnchorDay = 15 // subscription renews on the 15th
```

Days 29-31 are fine — the server clamps to short months. Changing the value
later (e.g., after a resubscribe) is safe; windows are computed server-side at
read time. The `X-Quota-Anchor-Day` header is consumed by the Gate/AI proxy
and stripped before the request reaches the AI provider.

**Read remaining quota** from any successful response — when the gate has
device limits configured, the proxy adds `X-Quota-Requests-Remaining`,
`X-Quota-Tokens-Remaining`, `X-Quota-Requests-Reset`, `X-Quota-Tokens-Reset`,
`X-Quota-Requests-Limit`, and `X-Quota-Tokens-Limit` headers (each reset is
the ISO 8601 reset time of that metric's binding window; each limit is that
window's total budget), exposed as `response.quotaStatus`:

```kotlin
val response = client.performProxyRequest(
    path = "openai/chat/completions",
    method = HttpMethod.POST,
    body = requestBody.toByteArray(),
    additionalHeaders = mapOf("Content-Type" to "application/json")
)

response.quotaStatus?.let { quota ->
    println("${quota.requestsRemaining} requests left, resets at ${quota.requestsResetAt}")
    // Render a usage meter: requestsUsed/tokensUsed and the 0.0-1.0 fractions
    quota.requestsUsedFraction?.let { progressBar.progress = it.toFloat() }
}
```

**Handle the structured 429** — when a request is rejected for exceeding a
limit, the thrown `GateApiException` carries a parsed `RateLimitInfo`:

```kotlin
try {
    client.performProxyRequest(...)
} catch (e: GateApiException) {
    val info = e.rateLimitInfo
    if (info != null) {
        when (info.window) {
            is RateLimitInfo.Window.Cycle,
            is RateLimitInfo.Window.Monthly ->
                showPaywall(resetsAt = info.resetsAt) // upgrade moment
            else ->
                showTryAgainLater(info.message)
        }
    } else {
        // Not a Gate/AI rate-limit rejection
        throw e
    }
}
```

`info.window` decodes unknown-safely: window types added in future server
versions arrive as `Window.Unknown(rawValue)` instead of failing to parse.

## Error Handling

```kotlin
import com.gateai.sdk.network.GateApiException
import com.gateai.sdk.playintegrity.IntegrityException

try {
    val response = client.performProxyRequest(...)
} catch (e: IntegrityException) {
    // Play Integrity failure - see error message for details
    Log.e("GateAI", "Integrity error: ${e.message}", e)
} catch (e: GateApiException) {
    // API error (network, auth, etc.)
    Log.e("GateAI", "API error: ${e.message}", e)
} catch (e: Exception) {
    // Other errors
    Log.e("GateAI", "Unexpected error", e)
}
```

## Best Practices

### 1. Initialize Once

Create the `GateAIClient` once and reuse it:

```kotlin
class MyApplication : Application() {
    lateinit var gateClient: GateAIClient
        private set
    
    override fun onCreate() {
        super.onCreate()
        gateClient = GateAIClient.create(this, configuration)
    }
}
```

### 2. Use Development Token for Testing

```kotlin
val configuration = GateAIConfiguration(
    // ...
    developmentToken = if (BuildConfig.DEBUG) {
        BuildConfig.GATE_DEV_TOKEN
    } else {
        null
    }
)
```

### 3. Handle Errors Gracefully

```kotlin
try {
    val response = client.performProxyRequest(...)
    if (response.status == 200) {
        // Success
    } else {
        // Handle HTTP error
    }
} catch (e: IntegrityException) {
    // Show user-friendly message, fall back to alternative
} catch (e: Exception) {
    // Log and show generic error
}
```

### 4. Set Log Level Based on Build Type

```kotlin
logLevel = if (BuildConfig.DEBUG) {
    GateAIConfiguration.LogLevel.DEBUG
} else {
    GateAIConfiguration.LogLevel.WARN
}
```

## Requirements

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Kotlin**: 1.9+
- **Google Play Services**: Required for Play Integrity on production devices

## License

Apache License 2.0

## Support

For issues, questions, or feature requests:
- GitHub Issues: [https://github.com/YOUR_ORG/GateAI/issues](https://github.com/YOUR_ORG/GateAI/issues)
- Email: support@gate-ai.net

## Additional Documentation

- [Getting Started](GETTING_STARTED.md) - Detailed setup and first release guide
- [Publishing Guide](PUBLISHING.md) - How to publish to Maven Central/JitPack
- [Distribution](DISTRIBUTION.md) - Quick reference for distribution commands
- [Changelog](CHANGELOG.md) - Version history and release notes
