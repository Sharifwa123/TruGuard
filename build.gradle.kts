// Plugins that share the Kotlin compiler classloader are declared once here. The Android Gradle
// Plugin and KSP are applied only inside the Android modules so that `-PengineOnly=true` builds
// never need to resolve them.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
