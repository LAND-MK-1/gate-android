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

```kotlin
dependencies {
    implementation("com.gateai.sdk:gateai:1.0.0")
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
4. Note your **Cloud Project Number** (you'll need this)

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

### Step 4: Test Your Integration

1. **Build a signed APK/AAB** with your release key
2. **Upload to Play Console** (Internal Testing track is fine)
3. **Install from Play Store** (must be installed via Play Store, not sideloaded)
4. **Run your app** and test authentication

### Step 5: Monitor for Errors

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

The SDK automatically includes these headers on all requests:

- `X-Client-Locale` - User's language/region (e.g., "en-US")
- `X-App-Version` - App version from manifest
- `X-OS-Version` - Android OS version
- `X-Device-Identifier` - Android ID (per-app, per-device)
- `X-Device-Type` - Device manufacturer and model
- `X-User-Status` - Custom user segment (if set)

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
