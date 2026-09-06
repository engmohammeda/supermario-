pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Two modules, not nine: :app is the UI and :engine is the Kotlin/JNI boundary
// around libmariobox_host.so. docs/PLAN.md §4 sketches a core/*+feature/* split;
// that stays the target shape, but splitting before the JNI surface is exercised on
// a device only multiplies the number of build scripts that have to be right. See
// docs/PLAN.md "التجاوزات" for the recorded decision.
rootProject.name = "MarioBox"
include(":engine", ":app")
