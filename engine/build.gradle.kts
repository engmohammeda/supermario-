// :engine -- the Kotlin/JNI boundary around libmariobox_host.so and the cores.
//
// Everything native lives under src/main/cpp and is built by that directory's
// CMakeLists.txt: libmariobox_host.so (the emulator host) and libmbcore_fceumm.so
// (the libretro core, dlopen'd at runtime by absolute path). AGP drops both into the
// APK's ABI lib dir, and the app hands the core's path to the host -- which is how a
// second core stays a drop-in instead of a link-time decision.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.mariobox.engine"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")

        // Debug builds ship one ABI so a CI run does not sit through 650 C files
        // per ABI. Release adds the 32-bit phone and the emulator image.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments("-DCMAKE_BUILD_TYPE=RelWithDebInfo")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    buildTypes {
        getByName("debug") {
            ndk { abiFilters.clear(); abiFilters += listOf("arm64-v8a") }
        }
        getByName("release") {
            isMinifyEnabled = false
            ndk { abiFilters += listOf("armeabi-v7a", "x86_64") }
            externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=Release") } }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Lint is run explicitly in CI (`lintDebug`), never as a build gate: a
        // warning about a missing translation in values-en must not stop a release.
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
