import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** Machine-specific settings, git-ignored; the release key is configured here (see AGENTS.md). */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "t7helpers.folderplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "t7helpers.folderplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        val storeFile = localProperties.getProperty("release.storeFile")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = file(localProperties.getProperty("release.storePasswordFile")).readText().trim()
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the key in local.properties, release builds come out unsigned.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    // The player (ExoPlayer) and the media session: background playback, the notification,
    // headset and lock screen controls.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests; this is the real one.
    testImplementation(libs.json)
}
