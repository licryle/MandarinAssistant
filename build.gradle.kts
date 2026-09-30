import org.gradle.api.tasks.testing.Test

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.composePlugin) apply false

    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.crashlytics) apply false

    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.navigation) apply false
    alias(libs.plugins.androidKMP) apply false
    alias(libs.plugins.androidLint) apply false
}

// Minimal logging to see test results in the console
subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

// Single target to run all local tests
tasks.register("allTests") {
    group = "verification"
    description = "Run all local unit tests across all modules"
    
    subprojects.forEach { prj ->
        // Depends on the 'test' task of each module, which aggregates target-specific tests
        dependsOn(prj.tasks.matching { it.name == "test" })
    }
}

// Maestro E2E (flows in e2e/, see e2e/README.md).
// Deliberately NOT wired into `check`: needs the Maestro CLI plus a booted
// emulator/device and takes minutes (177MB asset DB on first launch).
// Usage:
//   ./gradlew maestroTest                          # full suite (fresh debug APK)
//   ./gradlew maestroTest -Pmaestro.flow=e2e/dictionary/01_dict_search.yaml
tasks.register<Exec>("maestroTest") {
    group = "verification"
    description = "Install debug APK, then run Maestro E2E flows (requires Maestro CLI + emulator/device)"

    dependsOn(":androidApp:installDebug")

    val e2eDir = rootDir.resolve("e2e")
    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val flow = providers.gradleProperty("maestro.flow").getOrElse("e2e")

    executable(if (isWindows) "cmd" else "maestro")
    if (isWindows) args("/c", "maestro", "test", flow)
    else args("test", flow)

    doFirst {
        // Fail fast with an actionable message when the CLI is missing.
        val probe = if (isWindows) listOf("cmd", "/c", "where", "maestro")
                    else listOf("sh", "-c", "command -v maestro")
        val found = try {
            providers.exec { commandLine(probe) }.result.get().exitValue == 0
        } catch (_: Exception) { false }
        if (!found) {
            throw GradleException(
                "Maestro CLI not found on PATH. Install it " +
                "(https://maestro.mobile.dev/getting-started/installing-maestro), " +
                "boot an emulator (or plug a device), then re-run. See e2e/README.md."
            )
        }
        if (!e2eDir.isDirectory) throw GradleException("e2e/ directory missing at $e2eDir.")
    }
}
