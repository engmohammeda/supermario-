// :app -- Compose UI, no emulation knowledge beyond :engine's API.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// CI injects versionName/versionCode from git tags ("v0.2.0" etc.); local builds
// fall back to the checked-in 0.2.0.
val releaseVersionName: String =
    providers.gradleProperty("versionName").getOrElse("0.2.0")
val releaseVersionCode: Int =
    providers.gradleProperty("versionCode").getOrElse("2").toInt()

// Release signing comes from CI secrets when present (see .github/workflows);
// otherwise the debug key keeps sideload testing possible.
val keystorePath: String = System.getenv("MARIOBOX_KEYSTORE_PATH") ?: ""
val hasReleaseKeystore = keystorePath.isNotBlank()

android {
    namespace = "dev.mariobox.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.mariobox"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MARIOBOX_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("MARIOBOX_KEY_ALIAS") ?: "mariobox"
                keyPassword = System.getenv("MARIOBOX_KEY_PASSWORD")
                    ?: System.getenv("MARIOBOX_KEYSTORE_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        getByName("release") {
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    lint { abortOnError = false }
}

kotlin {
    jvmToolchain(21)
    composeCompiler {
        // Stable IDs keep Compose UI tests and layout inspectors meaningful across
        // rebuilds; harmless when tests are not run.
        includeSourceInformation = true
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
