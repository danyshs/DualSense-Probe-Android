pluginManagement {
    // the offline cache holds the Kotlin plugin jars but not their plugin-id markers
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.android" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                "org.jetbrains.kotlin.plugin.compose" ->
                    useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DualSense-Probe-Android"

include(":pad")
// :app joins the build the moment its folder exists
if (file("app").isDirectory) include(":app")
