plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

group = "com.gateai.sdk"

android {
    namespace = "com.gateai.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

configurations.all {
    if (name.endsWith("RuntimeClasspathCopy")) {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.okhttp)
    implementation(libs.google.play.integrity)
    implementation(libs.androidx.security.crypto)

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

// Read version from gradle.properties
val versionName: String by project

android {
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.gateai.sdk"
            artifactId = "gateai"
            version = versionName

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Gate/AI Android SDK")
                description.set("Android SDK for Gate/AI authentication using Play Integrity, DPoP, and hardware-backed device keys")
                url.set("https://github.com/YOUR_ORG/GateAI")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("gateai")
                        name.set("Gate/AI Team")
                        email.set("support@gate-ai.net")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/YOUR_ORG/GateAI.git")
                    developerConnection.set("scm:git:ssh://github.com/YOUR_ORG/GateAI.git")
                    url.set("https://github.com/YOUR_ORG/GateAI")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YOUR_ORG/GateAI")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }

        maven {
            name = "MavenCentral"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (versionName.endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    // Only sign if signing credentials are configured
    val signingKey = project.findProperty("signing.keyId") as String?
        ?: System.getenv("SIGNING_KEY_ID")
    if (signingKey != null) {
        sign(publishing.publications["release"])
    }
}

// Convenience tasks
tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Publishes to Maven Central"
    dependsOn("publishReleasePublicationToMavenCentralRepository")
}

tasks.register("publishToGitHub") {
    group = "publishing"
    description = "Publishes to GitHub Packages"
    dependsOn("publishReleasePublicationToGitHubPackagesRepository")
}

