# Gate/AI Android SDK

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Min SDK](https://img.shields.io/badge/minSdk-24-green.svg)
![License](https://img.shields.io/badge/license-Apache%202.0-orange.svg)

Android Kotlin library for Gate/AI authentication using Play Integrity, DPoP (Demonstrating Proof-of-Possession), and hardware-backed device keys. This SDK provides secure, device-bound authentication for Android applications connecting to AI services through the Gate/AI proxy.

## Features

- 🔐 **Hardware-Backed Key Storage**: Uses Android Keystore with StrongBox preference for P-256 ECDSA keys
- 📱 **Play Integrity Attestation**: Integrates Google Play Integrity API for device verification
- 🔑 **DPoP Token Generation**: Creates signed proof-of-possession tokens for each request
- ♻️ **Automatic Token Refresh**: Manages token lifecycle with 60-second grace period
- 🧪 **Development Token Flow**: Supports emulator testing without Play Integrity
- 🚀 **Ktor-based HTTP Client**: Modern coroutine-based networking
- 📊 **Configurable Logging**: Debug, info, and error log levels

## Installation

### Option 1: Maven Central (Recommended)

Add to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.gateai.sdk:gateai:1.0.0")
}
```

### Option 2: JitPack

Add JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.YOUR_ORG:gate-android:v1.0.0")
}
```

### Option 3: Local Development (Composite Build)

For local development or testing unreleased versions:

1. Clone this repository alongside your app project
2. In your app's `settings.gradle.kts`:

```kotlin
includeBuild("../gate-android")
```

3. In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.gateai.sdk:gateai")
}
```

## Quick Start

### 1. Add Required Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 2. Configure Gate/AI Credentials

Obtain your credentials from the Gate/AI Portal:
- Register your Android package name
- Add your app's SHA-256 signing certificate fingerprint
- Note your Gate/AI base URL (e.g., `https://yourteam.us01.gate-ai.net`)

**Get your signing certificate fingerprint:**

```bash
# For debug keystore
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA256

# For release keystore
keytool -list -v -keystore /path/to/your/release.keystore -alias your-key-alias
```

### 3. Initialize the SDK

Create a custom `Application` class:

```kotlin
class MyApp : Application() {
    lateinit var gateAIClient: GateAIClient
        private set

    override fun onCreate() {
        super.onCreate()
        
        val configuration = GateAIConfiguration(
            baseUrl = "https://yourteam.us01.gate-ai.net",
            packageName = packageName,
            signingCertSha256 = "AA:BB:CC:DD:...", // Your app's signing cert
            developmentToken = null, // Only for emulator/staging
            logLevel = GateAIConfiguration.LogLevel.INFO
        )

        gateAIClient = GateAIClient.create(
            context = this,
            configuration = configuration
        )
    }
}
```

Register in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApp"
    ...>
```

### 4. Make Authenticated Requests

#### Get Authorization Headers

```kotlin
class MyViewModel(application: Application) : AndroidViewModel(application) {
    private val gateAI = (application as MyApp).gateAIClient

    fun callOpenAI() {
        viewModelScope.launch {
            try {
                // Get auth headers for the request
                val headers = gateAI.authorizationHeaders(
                    path = "openai/chat/completions",
                    method = HttpMethod.POST
                )
                
                // Use with your HTTP client
                val response = httpClient.post("https://yourteam.us01.gate-ai.net/openai/chat/completions") {
                    headers {
                        headers.forEach { (key, value) -> append(key, value) }
                    }
                    setBody(yourRequest)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

#### Perform Proxy Request (All-in-One)

```kotlin
viewModelScope.launch {
    try {
        val requestBody = """
            {
                "model": "gpt-4",
                "messages": [{"role": "user", "content": "Hello!"}]
            }
        """.trimIndent()

        val response = gateAI.performProxyRequest(
            path = "openai/chat/completions",
            method = HttpMethod.POST,
            body = requestBody.toByteArray(),
            additionalHeaders = mapOf("Content-Type" to "application/json")
        )

        val responseText = String(response.body)
        // Handle response
    } catch (e: GateApiException) {
        // Handle API error
        Log.e("GateAI", "Error: ${e.statusCode} - ${e.errorBody}")
    }
}
```

## Configuration Options

### GateAIConfiguration

```kotlin
data class GateAIConfiguration(
    val baseUrl: String,              // Gate/AI proxy URL
    val packageName: String,          // Your app's package name
    val signingCertSha256: String,    // SHA-256 fingerprint (colon or raw hex)
    val developmentToken: String? = null,  // For emulator/staging only
    val logLevel: LogLevel = LogLevel.WARN // Logging verbosity
) {
    enum class LogLevel { DEBUG, INFO, WARN, ERROR, NONE }
}
```

### Development Token Flow

For testing on emulators or in staging environments where Play Integrity is unavailable:

1. Generate a development token in the Gate/AI Portal
2. **IMPORTANT**: Never include dev tokens in production builds!

```kotlin
val configuration = GateAIConfiguration(
    baseUrl = "https://yourteam.us01.gate-ai.net",
    packageName = packageName,
    signingCertSha256 = BuildConfig.SIGNING_CERT_SHA256,
    developmentToken = if (BuildConfig.DEBUG) BuildConfig.DEV_TOKEN else null,
    logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
)
```

**Configure in `build.gradle.kts`:**

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "SIGNING_CERT_SHA256", "\"YOUR_SHA256\"")
        buildConfigField("String", "DEV_TOKEN", "\"\"") // Empty for release
    }
    
    buildTypes {
        debug {
            buildConfigField("String", "DEV_TOKEN", "\"your-dev-token-here\"")
        }
    }
}
```

## Advanced Usage

### Error Handling

```kotlin
try {
    val headers = gateAI.authorizationHeaders(...)
} catch (e: GateApiException) {
    when (e.statusCode) {
        401 -> {
            // Unauthorized - check DPoP nonce
            val nonce = e.headers["DPoP-Nonce"]
            // SDK automatically retries with nonce
        }
        403 -> // Forbidden - invalid signature/attestation
        429 -> // Rate limited
        else -> // Other error
    }
}
```

### Custom Logging

```kotlin
class MyLogger : GateLogger {
    override fun debug(message: String) = Timber.d(message)
    override fun info(message: String) = Timber.i(message)
    override fun warn(message: String) = Timber.w(message)
    override fun error(message: String, throwable: Throwable?) = Timber.e(throwable, message)
}

val gateAI = GateAIClient.create(
    context = this,
    configuration = configuration,
    logger = MyLogger()
)
```

### DPoP Nonce Handling

The SDK automatically handles DPoP nonce challenges:

1. Makes initial request without nonce
2. If server returns 401 with `DPoP-Nonce` header
3. Automatically retries with the nonce

This is transparent when using `performProxyRequest()`.

## Architecture

```
gate-android/
  └── gateai/
      └── src/main/java/com/gateai/sdk/
          ├── core/           # GateAIClient, configuration, HTTP methods
          ├── auth/           # Challenge/token exchange API client
          ├── security/       # Device key management (Keystore/StrongBox)
          ├── playintegrity/  # Play Integrity API wrapper
          ├── network/        # Ktor HTTP client, error handling
          ├── util/           # Base64URL, ECDSA signing, JWT creation
          └── logging/        # Logging abstraction
```

### Authentication Flow

```
┌─────────────┐
│   App       │
└──────┬──────┘
       │ 1. authorizationHeaders()
       ▼
┌─────────────────────┐
│  GateAIClient       │
└──────┬──────────────┘
       │ 2. ensureKey()
       ▼
┌─────────────────────┐       ┌──────────────────┐
│ DeviceKeyManager    │◄──────┤ Android Keystore │
└──────┬──────────────┘       └──────────────────┘
       │ 3. Device key (JWK)
       ▼
┌─────────────────────┐
│  obtainToken()      │
└──────┬──────────────┘
       │ 4. fetchChallenge()
       ▼
┌─────────────────────┐       ┌──────────────────┐
│  AuthApiClient      │──────►│  /attest/        │
└──────┬──────────────┘       │   challenge      │
       │ 5. Challenge (nonce) └──────────────────┘
       ▼
┌─────────────────────┐       ┌──────────────────┐
│ PlayIntegrityMgr    │──────►│ Play Integrity   │
└──────┬──────────────┘       │    API           │
       │ 6. Integrity token   └──────────────────┘
       ▼
┌─────────────────────┐
│  DPoPBuilder        │
└──────┬──────────────┘
       │ 7. DPoP proof (JWT)
       ▼
┌─────────────────────┐       ┌──────────────────┐
│  AuthApiClient      │──────►│  /token          │
└──────┬──────────────┘       └──────────────────┘
       │ 8. Access token
       ▼
┌─────────────────────┐
│  Return headers:    │
│  - Authorization    │
│  - DPoP             │
└─────────────────────┘
```

## Sample App

See [`gate-android-sample/`](../gate-android-sample) for a complete Jetpack Compose example.

**Run the sample:**

```bash
cd gate-android-sample
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Publishing

See [PUBLISHING.md](./PUBLISHING.md) for details on:
- Publishing to Maven Central
- Publishing to JitPack
- Publishing to GitHub Packages
- Local Maven installation

## Requirements

- **Min SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **Compile SDK**: Android 14 (API 34)
- **Kotlin**: 2.1.0+
- **Java**: 17

## Dependencies

- AndroidX Core KTX
- AndroidX AppCompat
- AndroidX Security Crypto (EncryptedSharedPreferences)
- Kotlin Coroutines
- Kotlinx Serialization JSON
- Ktor Client (Core, OkHttp, Content Negotiation)
- Google Play Integrity API
- OkHttp

## Security Considerations

### Device Key Storage

- Keys are stored in Android Keystore
- StrongBox backing is used when available (Android 9+)
- Private keys are non-exportable
- Keys are bound to the app's package name

### Play Integrity

- Ensures the app is running on a genuine Android device
- Verifies app hasn't been tampered with
- Validates proper app signing

### DPoP Tokens

- Per-request proof-of-possession prevents token replay
- Tokens are bound to specific HTTP method + URL
- Includes JTI (unique ID) and timestamp

## Troubleshooting

### "Play Integrity API Error"

- Ensure Play Integrity is enabled for your app in Google Play Console
- Verify your package name and signing certificate are registered in Gate/AI Portal
- Use development token flow for emulator testing

### "Invalid Signature"

- Check that your SHA-256 fingerprint matches the one registered in Gate/AI Portal
- Ensure you're using the correct keystore (debug vs release)

### "Token Expired"

- SDK automatically refreshes tokens 60 seconds before expiry
- Check device clock is synchronized

### Build Issues

- Ensure Gradle 8.10+ and Android Gradle Plugin 8.7.3+
- Clear Gradle cache: `./gradlew clean --no-configuration-cache`

## References

- [Proxy AUTH-Android.md](../Proxy/AUTH-Android.md) – Android-specific auth flow
- [Proxy AUTH-Simulators.md](../Proxy/AUTH-Simulators.md) – Development token flow
- [Proxy AUTH-ClientLibrary.md](../Proxy/AUTH-ClientLibrary.md) – Cross-platform expectations
- [iOS SDK](../gate-ios/) – iOS implementation for reference

## License

Apache License 2.0 - see [LICENSE](../LICENSE) file for details.

## Support

For issues, questions, or contributions:
- Open an issue on GitHub
- Contact: support@gate-ai.net
- Documentation: https://docs.gate-ai.net

---

Built with ❤️ by the Gate/AI Team
