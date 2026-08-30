# Changelog

All notable changes to the Gate/AI Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-08-30

### Added
- `GateAIClient.userTier` property — the user's plan tier (e.g., "free", "pro"), sent as
  the `X-User-Tier` header; Portal-configured per-tier usage limits match it exactly
  (case-sensitive), while `userStatus` remains free-form analytics segmentation
- `GateAIClient.quotaAnchorDay` property — day-of-month (1-31) the user's subscription
  renews, sent as the `X-Quota-Anchor-Day` header to anchor billing-cycle device usage
  windows (invalid values are dropped with a warning)
- `QuotaStatus` — remaining device quota parsed from the `X-Quota-Requests-Remaining`,
  `X-Quota-Tokens-Remaining`, `X-Quota-Requests-Reset`, and `X-Quota-Tokens-Reset`
  response headers (per-metric reset times), exposed as `RawResponse.quotaStatus`
- `RateLimitInfo` — structured rate-limit rejection details (code, message, window,
  limit, used, resetsAt) parsed from 429 `rate_limited` bodies, exposed as
  `GateApiException.rateLimitInfo`; window decoding is unknown-safe
  (`Window.Unknown(rawValue)`)
- Unit tests for quota header parsing, 429 body parsing, anchor-day validation, and
  ISO 8601 date parsing
- README "Usage limits & quotas" section

## [1.0.0] - 2026-08-27

### Fixed
- Token exchange now includes the attestation challenge nonce required by the server
- DPoP header serializes the device public key JWK correctly (previously crashed the signer)
- JSON encoding emits `platform` and attestation `type` discriminator fields and omits null optionals
- Play Integrity requests support `cloudProjectNumber` (required for sideloaded installs; Google error -16 otherwise)

### Added
- `GateAIConfiguration.cloudProjectNumber` for classic Play Integrity requests
- Initial release of Gate/AI Android SDK
- Hardware-backed device key management using Android Keystore
- StrongBox preference for enhanced security (Android 9+)
- P-256 ECDSA key generation with JWK export (RFC 7517)
- JWK thumbprint calculation (RFC 7638)
- Play Integrity API integration for device attestation
- DPoP (Demonstrating Proof-of-Possession) token generation (RFC 9449)
- Automatic token lifecycle management with 60-second grace period
- Token caching and refresh
- `/attest/challenge` API integration
- `/token` exchange API with DPoP proof
- Automatic DPoP nonce retry handling (401 with DPoP-Nonce header)
- `authorizationHeaders()` method for custom HTTP client integration
- `performProxyRequest()` method for all-in-one proxied requests
- Development token flow for emulator/simulator testing
- Ktor-based HTTP client with OkHttp engine
- Configurable logging (DEBUG, INFO, WARN, ERROR, NONE)
- Comprehensive error handling with `GateApiException`
- ProGuard/R8 consumer rules for proper code shrinking
- Complete Kotlin documentation
- Jetpack Compose sample app demonstrating SDK usage

### Security
- Private keys are non-exportable from Android Keystore
- Keys are bound to app package name
- DER to raw signature conversion for ES256 (prevents malleability)
- Secure nonce handling with Play Integrity
- HTTPS-only communication enforced

### Documentation
- Comprehensive README with quick start guide
- PUBLISHING.md with distribution instructions
- Sample app with Compose UI
- Inline KDoc for all public APIs
- Architecture diagrams and flow charts

## [Unreleased]

### Planned
- Analytics headers support
- Instrumented tests with Play Integrity mocking
- Unit tests for key components
- Kotlin Multiplatform support consideration
- Token refresh scheduling optimizations
- Network retry policies
- Request/response logging interceptor

[1.0.0]: https://github.com/YOUR_ORG/GateAI/releases/tag/v1.0.0

