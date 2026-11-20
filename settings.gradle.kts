pluginManagement {
    // the offline cache holds the Kotlin plugin jars but not its plugin-id marker
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.jvm") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
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
