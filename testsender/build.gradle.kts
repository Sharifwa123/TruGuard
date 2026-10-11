import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val appVersionName: String = versionProps.getProperty("VERSION_NAME").trim()
val appVersionCode: Int = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersionName.substringBefore('-'))
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt() }
    ?: error("VERSION_NAME '$appVersionName' must look like MAJOR.MINOR.PATCH")

android {
    namespace = "com.shariftechnologies.scammonitor.testsender"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shariftechnologies.scammonitor.testsender"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    val signingKeystore = System.getenv("SIGNING_KEYSTORE_PATH")
    if (!signingKeystore.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Deliberately framework-only: this helper app posts synthetic notifications and nothing else.
}
