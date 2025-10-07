# Gate/AI Android SDK - Distribution Quick Reference

## 📦 Pre-Distribution Checklist

- [ ] Update `versionName` in `gradle.properties`
- [ ] Update `CHANGELOG.md` with release notes
- [ ] Run tests: `./gradlew test`
- [ ] Run lint: `./gradlew lint`
- [ ] Build release: `./gradlew assembleRelease`
- [ ] Test with sample app
- [ ] Update README version badges
- [ ] Commit all changes
- [ ] Create Git tag: `git tag v1.0.0`
- [ ] Push tag: `git push origin v1.0.0`

## 🚀 Publishing Commands

### Local Testing (Recommended First)

```bash
cd gate-android
./gradlew publishToMavenLocal --no-configuration-cache
```

Publishes to `~/.m2/repository/com/gateai/sdk/gateai/1.0.0/`

**Test locally in another project:**
```kotlin
// settings.gradle.kts
repositories {
    mavenLocal()
    // ...
}

// build.gradle.kts
dependencies {
    implementation("com.gateai.sdk:gateai:1.0.0")
}
```

---

### JitPack (Easiest - Auto-builds from GitHub)

**No commands needed!** JitPack automatically builds from GitHub releases.

1. **Create GitHub release:**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   Then create release on GitHub

2. **Users add JitPack:**
   ```kotlin
   // settings.gradle.kts
   repositories {
       maven { url = uri("https://jitpack.io") }
   }
   
   dependencies {
       implementation("com.github.YOUR_ORG:gate-android:v1.0.0")
   }
   ```

3. **Check build status:** https://jitpack.io/#YOUR_ORG/gate-android

---

### GitHub Packages (Private Distribution)

**Setup** (one-time):
```properties
# ~/.gradle/gradle.properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN  # Generate at github.com/settings/tokens
```

**Publish:**
```bash
cd gate-android
./gradlew publishToGitHub --no-configuration-cache
```

**Update repository URL** in `gateai/build.gradle.kts` before publishing:
```kotlin
url = uri("https://maven.pkg.github.com/YOUR_ORG/GateAI")
```

---

### Maven Central (Production - Requires Setup)

**Setup** (one-time):

1. **Create Sonatype account:** https://issues.sonatype.org
2. **Create Jira ticket** to claim `com.gateai` group
3. **Generate GPG key:**
   ```bash
   gpg --gen-key
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```

4. **Add to `~/.gradle/gradle.properties`:**
   ```properties
   signing.keyId=YOUR_KEY_ID
   signing.password=YOUR_GPG_PASSWORD
   signing.secretKeyRingFile=/Users/you/.gnupg/secring.gpg
   
   ossrhUsername=YOUR_SONATYPE_USERNAME
   ossrhPassword=YOUR_SONATYPE_PASSWORD
   ```

**Publish:**
```bash
cd gate-android
./gradlew publishToMavenCentral --no-configuration-cache
```

**Release on Sonatype:**
1. Login to https://s01.oss.sonatype.org
2. Go to "Staging Repositories"
3. Find your repository
4. Click "Close"
5. Click "Release"
6. Wait ~10 minutes for Maven Central sync

---

## 📝 Version Management

Edit `gate-android/gradle.properties`:
```properties
versionName=1.0.0
```

**Semantic Versioning:**
- **1.0.0 → 2.0.0**: Breaking changes
- **1.0.0 → 1.1.0**: New features (backward compatible)
- **1.0.0 → 1.0.1**: Bug fixes

**Snapshot versions** (for pre-release testing):
```properties
versionName=1.1.0-SNAPSHOT
```

---

## 🔍 Verify Published Artifacts

### Check Local Maven
```bash
ls -lh ~/.m2/repository/com/gateai/sdk/gateai/1.0.0/
```

Should contain:
- `gateai-1.0.0.aar` (main library)
- `gateai-1.0.0.pom` (Maven metadata)
- `gateai-1.0.0.module` (Gradle metadata)
- `gateai-1.0.0-sources.jar` (source code)
- `gateai-1.0.0-javadoc.jar` (documentation)

### Check Maven Central
```bash
curl https://repo1.maven.org/maven2/com/gateai/sdk/gateai/maven-metadata.xml
```

### Check JitPack
Visit: https://jitpack.io/#YOUR_ORG/gate-android

### Check GitHub Packages
Visit: https://github.com/YOUR_ORG/GateAI/packages

---

## 🔧 Available Gradle Tasks

```bash
# View all publishing tasks
./gradlew tasks --group publishing

# Key tasks:
publishToMavenLocal          # Publish to ~/.m2/repository
publishToGitHub              # Publish to GitHub Packages
publishToMavenCentral        # Publish to Maven Central
publish                      # Publish to all configured repos
```

---

## 🐛 Troubleshooting

### Signing Errors

**Error:** "Cannot perform signing task because it has no configured signatory"

**Solution:**
- For local testing: Signing is automatically skipped
- For production: Add signing credentials to `~/.gradle/gradle.properties`

### Version Conflicts

**Error:** "Could not find versionName property"

**Solution:** Ensure `versionName=1.0.0` is in `gate-android/gradle.properties`

### Build Cache Issues

```bash
# Clear everything and rebuild
./gradlew clean --no-configuration-cache
./gradlew build --no-configuration-cache
```

### Publishing Already Exists

**Error:** "Repository already contains version 1.0.0"

**Solution:**
- For Maven Central: Cannot re-publish same version
- For local: Delete `~/.m2/repository/com/gateai/`
- For production: Increment version number

---

## 📚 Additional Resources

- **Full Publishing Guide:** [PUBLISHING.md](./PUBLISHING.md)
- **SDK Documentation:** [README.md](./README.md)
- **Changelog:** [CHANGELOG.md](./CHANGELOG.md)
- **Sample App:** [gate-android-sample/README.md](../gate-android-sample/README.md)

---

## 🎯 Quick Publishing Workflow

**For development/testing:**
```bash
./gradlew publishToMavenLocal --no-configuration-cache
```

**For JitPack (easiest public release):**
```bash
git tag v1.0.0
git push origin v1.0.0
# Create GitHub release
```

**For Maven Central (production):**
```bash
# 1. Update version
vim gradle.properties  # versionName=1.0.0

# 2. Build and test
./gradlew clean build

# 3. Publish
./gradlew publishToMavenCentral --no-configuration-cache

# 4. Release on Sonatype portal
# Visit https://s01.oss.sonatype.org
```

---

Built with ❤️ by the Gate/AI Team

