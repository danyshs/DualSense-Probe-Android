plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.dsprobe.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.dsprobe.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = false
        // each disabled check has its reason; none is turned off to hide a finding in our own code
        disable += "OldTargetApi" // target 35 is the newest platform AGP 8.4.2 builds against
        disable += "AndroidGradlePluginVersion" // AGP 8.4.2 is the cached one; moving it is its own change
        disable += "GradleDependency" // Jetpack pins stay at what AGP 8.4.2 accepts (see libs.versions.toml)
        disable += "LockedOrientationActivity" // the design locks the app to portrait
        disable += "DiscouragedApi" // same portrait lock, reported a second time
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":pad"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // a gate prints what it measured
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent != null) return
            println("app tests: ${result.testCount} run, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped")
            check(result.testCount > 0) { "no tests ran" }
        }
    })
}
