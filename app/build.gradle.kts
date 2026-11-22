plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release version: the release workflow passes -PversionName=1.2.3 (from the release tag v1.2.3).
// versionCode is derived from it (1.2.3 -> 10203) so every release installs over the previous one.
// Local builds without the property use the fallback below. See docs/releasing.md.
val releaseVersionName = (findProperty("versionName") as String?)?.removePrefix("v") ?: "0.2.0"
val releaseVersionCode = releaseVersionName.split('.', '-').take(3).mapNotNull { it.toIntOrNull() }
    .let { p -> (p.getOrElse(0) { 0 } * 10000) + (p.getOrElse(1) { 0 } * 100) + p.getOrElse(2) { 0 } }
    .coerceAtLeast(1)

android {
    namespace = "dev.slipaway"
    compileSdk = 37

    defaultConfig {
        // If you change this, also change android:targetPackage in res/xml/shortcuts.xml.
        applicationId = "io.github.abhilesh1412.slipaway"
        minSdk = 29
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        // Where the app looks for updates (Settings -> Updates). Forks: change to your own "owner/repo".
        buildConfigField("String", "UPDATE_REPO", "\"ABHILESH1412/Slip-Away\"")
    }

    // Release signing comes from environment variables (GitHub Actions secrets, see docs/releasing.md):
    //   SLIPAWAY_KEYSTORE (path to the .jks), SLIPAWAY_STORE_PASSWORD, SLIPAWAY_KEY_ALIAS, SLIPAWAY_KEY_PASSWORD.
    // Without them, release builds use the debug key: fine for trying on your own phone, never for publishing.
    val releaseKeystore = System.getenv("SLIPAWAY_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SLIPAWAY_STORE_PASSWORD")
                keyAlias = System.getenv("SLIPAWAY_KEY_ALIAS") ?: "slipaway"
                keyPassword = System.getenv("SLIPAWAY_KEY_PASSWORD") ?: System.getenv("SLIPAWAY_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
