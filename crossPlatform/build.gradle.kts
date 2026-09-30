import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.*
import org.gradle.internal.os.OperatingSystem

var os: OperatingSystem? = OperatingSystem.current()
val isMac = OperatingSystem.current().isMacOsX

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.composePlugin)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.buildKonfig)
    alias(libs.plugins.androidKMP)
}

group = "fr.berliat.hskwidget"

val appVersionCode = libs.versions.app.versionCode.get()
val appVersionName = libs.versions.app.versionName.get()

val output = rootProject.file("iosApp/Version.xcconfig")
output.parentFile.mkdirs()
output.writeText(
    """
        APP_VERSION_NAME = $appVersionName
        APP_VERSION_CODE = $appVersionCode
        """.trimIndent() + "\n"
)

buildkonfig {
    packageName = "fr.berliat.hskwidget"
    defaultConfigs {
        buildConfigField(INT, "VERSION_CODE", appVersionCode)
        buildConfigField(STRING, "VERSION_NAME", appVersionName)
        buildConfigField(BOOLEAN, "DEBUG_MODE", "true")
    }
}

kotlin {
    android {
        namespace = "fr.berliat.hskwidget"
        compileSdk = 37
        minSdk = 26

        // Enables JVM host-side unit tests (commonTest) on this machine:
        // e.g. ./gradlew :crossPlatform:testDebugUnitTest
        withHostTest {
        }

        androidResources {
            enable = true
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
                excludes += "META-INF/INDEX.LIST"
                excludes += "META-INF/DEPENDENCIES"
            }
        }

        compilerOptions {
            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
            )
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "crossPlatform"
            isStatic = true
            export(project(":googledrivebackup"))
        }
    }

    swiftPMDependencies {
        swiftPackage(
            url = url("https://github.com/firebase/firebase-ios-sdk.git"),
            version = from("12.18.0"),
            products = listOf(
                product("FirebaseAnalytics"),
                product("FirebaseCrashlytics"),
                product("FirebaseCore")
            )
        )
        swiftPackage(
            url = url("https://github.com/google/GoogleSignIn-iOS.git"),
            version = from("9.2.0"),
            products = listOf(product("GoogleSignIn"))
        )
        swiftPackage(
            url = url("https://github.com/google/google-api-objectivec-client-for-rest.git"),
            version = from("5.4.0"),
            products = listOf(product("GoogleAPIClientForREST_Drive"))
        )
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.normalize)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.androidx.datastore)
                implementation(libs.androidx.datastore.preferences)
                api(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.resources)
                implementation(libs.kermit)
                implementation(libs.room.runtime)
                implementation(libs.sqlite.bundled)
                implementation(libs.filekit.core)
                implementation(libs.filekit.dialogs)
                implementation(libs.filekit.dialogs.compose)
                implementation(libs.camerak)
                implementation(libs.navigation.compose)
                implementation(project(":pinyin4kot"))
                implementation(project(":hsktextviews"))
                implementation(project(":googledrivebackup"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.compose.ui.tooling)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.androidx.room.sqlite.wrapper)
                implementation(libs.anki.android)
                implementation(project(":AnkiDroidAPIHelper"))
                implementation(libs.androidx.lifecycle.service)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.lifecycle.livedata.ktx)
                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.jieba.analysis)
                implementation(libs.text.recognition.chinese)
                implementation(libs.billing.ktx)
                implementation(libs.review.ktx)
                implementation(libs.androidx.documentfile)
                implementation(libs.play.services.auth)
                implementation(libs.google.api.services.drive)
                implementation(libs.androidx.constraintlayout)
                implementation(libs.material)

                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.analytics)
                implementation(libs.firebase.crashlytics)

                implementation("androidx.preference:preference-ktx:1.2.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.turbine)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                api(project(":googledrivebackup"))
            }
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.compilations["main"].defaultSourceSet.dependsOn(iosMain)
            target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    if (OperatingSystem.current().isMacOsX) {
        add("kspIosSimulatorArm64", libs.room.compiler)
        add("kspIosArm64", libs.room.compiler)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "fr.berliat.hskwidget"
    generateResClass = always
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

/*skie {
    build {
        produceDistributableFramework()
    }
}*/

val copyGeneratedDatabase = tasks.register("copyGeneratedDatabase") {
    group = "database"
    description = "Copies the generated Mandarin Assistant database to Android and iOS assets."

    val dbFile = file("${rootProject.projectDir}/database_generation/output/Mandarin_Assistant.db")

    doFirst {
        if (!dbFile.exists()) {
            throw GradleException(
                "Mandarin_Assistant.db not found in 'database_generation/' folder. " +
                "This file is required to build the application. " +
                "Please run the database generation script first."
            )
        }
    }

    doLast {
        // Copy to Android assets
        copy {
            from(dbFile)
            into(file("${projectDir}/src/androidMain/assets/databases"))
        }
        // Copy to iOS assets
        copy {
            from(dbFile)
            into(file("${rootProject.projectDir}/iosApp/hskwidget/databases"))
        }
    }
}

tasks.matching {
    it.name.startsWith("preBuild") ||
    it.name.startsWith("generate") ||
    it.name.contains("Package")
}.all {
    dependsOn(copyGeneratedDatabase)
}

// Fix implicit dependency between KSP and BuildKonfig
tasks.matching { it.name.startsWith("kspKotlin") }.configureEach {
    dependsOn("generateBuildKonfig")
}
