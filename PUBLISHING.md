# Publishing the Gate/AI Android SDK

This guide covers how to publish the Gate/AI Android SDK for distribution.

## Publishing Options

### Option 1: Maven Central (Recommended for Public Release)

Maven Central is the standard repository for Android libraries and provides the best developer experience.

#### Prerequisites

1. **Sonatype Account**: Create an account at [issues.sonatype.org](https://issues.sonatype.org)
2. **Group ID Verification**: Verify ownership of `com.gateai` or similar domain
3. **GPG Key**: Generate a GPG key for signing artifacts
4. **Credentials**: Store in `~/.gradle/gradle.properties`

#### Setup

1. **Add credentials to `~/.gradle/gradle.properties`**:
```properties
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg

ossrhUsername=YOUR_SONATYPE_USERNAME
ossrhPassword=YOUR_SONATYPE_PASSWORD
```

2. **Publish to Maven Central**:
```bash
cd gate-android
./gradlew publishToMavenCentral --no-configuration-cache
```

3. **Release on Sonatype**:
   - Log in to [s01.oss.sonatype.org](https://s01.oss.sonatype.org)
   - Navigate to "Staging Repositories"
   - Close and release your staging repository
   - Artifacts will sync to Maven Central within ~10 minutes

#### Usage After Publishing

Developers can then add to their `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.gateai.sdk:gateai:1.0.0")
}
```

---

### Option 2: JitPack (Easiest for Quick Distribution)

JitPack builds directly from GitHub releases, requiring minimal setup.

#### Setup

1. **Create a release on GitHub**:
   - Tag your release (e.g., `v1.0.0`)
   - Push the tag: `git tag v1.0.0 && git push origin v1.0.0`
   - Create a GitHub release from the tag

2. **JitPack automatically builds** from your release tag

#### Usage with JitPack

Add to your app's `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add to your app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.YOUR_ORG:gate-android:v1.0.0")
}
```

---

### Option 3: GitHub Packages (Private Distribution)

Good for internal distribution or private releases.

#### Setup

1. **Add GitHub token to `~/.gradle/gradle.properties`**:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

2. **Publish**:
```bash
cd gate-android
./gradlew publish
```

#### Usage with GitHub Packages

Add to your app's `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_ORG/GateAI")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

---

### Option 4: Local Maven Repository (Development)

For local testing before publishing.

#### Publish Locally

```bash
cd gate-android
./gradlew publishToMavenLocal
```

This installs to `~/.m2/repository/`

#### Usage from Local Maven

Add to your app's `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

Add to your app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.gateai.sdk:gateai:1.0.0-LOCAL")
}
```

---

## Version Management

Update the version in `gate-android/gradle.properties`:
```properties
VERSION_NAME=1.0.0
GROUP=com.gateai.sdk
```

Follow semantic versioning:
- **Major** (1.0.0 → 2.0.0): Breaking API changes
- **Minor** (1.0.0 → 1.1.0): New features, backward compatible
- **Patch** (1.0.0 → 1.0.1): Bug fixes

---

## Release Checklist

Before publishing a new version:

- [ ] Update `VERSION_NAME` in `gradle.properties`
- [ ] Update `CHANGELOG.md` with release notes
- [ ] Run full test suite: `./gradlew test`
- [ ] Run lint checks: `./gradlew lint`
- [ ] Build release AAR: `./gradlew assembleRelease`
- [ ] Test sample app with new version
- [ ] Update README with new version number
- [ ] Create Git tag: `git tag v1.0.0`
- [ ] Push tag: `git push origin v1.0.0`
- [ ] Create GitHub release with release notes
- [ ] Publish artifacts (Maven Central, JitPack, etc.)
- [ ] Verify installation in a fresh project

---

## Troubleshooting

### "Could not find com.gateai.sdk:gateai:1.0.0"

- Ensure the repository is added to your `settings.gradle.kts` or `build.gradle.kts`
- For Maven Central, wait ~10 minutes after release for sync
- For JitPack, check build status at `https://jitpack.io/#YOUR_ORG/gate-android`

### Signing Failures

- Verify GPG key is installed: `gpg --list-keys`
- Check `gradle.properties` has correct signing credentials
- Ensure key hasn't expired: `gpg --list-keys --keyid-format LONG`

### Build Cache Issues

- Clear Gradle cache: `./gradlew clean`
- Delete `~/.gradle/caches/` and rebuild
- Use `--no-configuration-cache` flag if needed

