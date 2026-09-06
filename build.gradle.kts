// Root build: nothing but plugin registration. Keeping every version in
// gradle/libs.versions.toml means the CI toolchain contract is one file.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
