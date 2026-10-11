// -PengineOnly=true builds only the pure-Kotlin detection engine. It exists for machines that have
// no Android SDK or cannot reach Google's Maven repository (e.g. restricted CI containers).
val engineOnly = providers.gradleProperty("engineOnly").orNull == "true"

pluginManagement {
    val engineOnly = providers.gradleProperty("engineOnly").orNull == "true"
    repositories {
        if (!engineOnly) google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!engineOnly) google()
        mavenCentral()
    }
}

rootProject.name = "ScamMonitor"

include(":engine")

if (!engineOnly) {
    include(":app")
    include(":testsender")
}
