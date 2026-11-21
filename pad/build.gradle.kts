plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    testLogging {
        events("failed")
        showStandardStreams = false
    }
    // a gate prints what it measured and fails when that is nothing
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent != null) return
            println(
                "pad tests: ${result.testCount} run, ${result.failedTestCount} failed, " +
                    "${result.skippedTestCount} skipped",
            )
            check(result.testCount > 0) { "no tests ran" }
        }
    })
}
