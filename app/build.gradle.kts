plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Each GitHub build gets a higher number, so a new APK installs over the old one.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The signing key and its password are GitHub secrets (SIGNING_KEYSTORE_BASE64 and
// SIGNING_PASSWORD); the workflow writes the key to signing/release.jks before building.
// Without them, builds use a temporary key.
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeystore = rootProject.file("signing/release.jks")
val hasReleaseKey = signingPassword != null && releaseKeystore.exists()

android {
    namespace = "com.wellsey.glimpse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wellsey.glimpse"
        minSdk = 30
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = releaseKeystore
                storePassword = signingPassword
                keyAlias = "apps"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
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
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Images: thumbnails, full-size photos, animated GIF/WebP
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    // Photo metadata (camera, exposure, GPS)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Video playback
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Map of geotagged photos (OpenStreetMap, no API key needed)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
