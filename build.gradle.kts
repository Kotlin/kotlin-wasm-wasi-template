@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.internal.os.OperatingSystem
import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.Companion.fromVersion
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport
import java.nio.file.Files
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.undercouchDownload) apply false
}

val kotlin_repo_url: String? = project.properties["kotlin_repo_url"] as String?
val kotlinLanguageVersionOverride = providers.gradleProperty("kotlin_language_version")
    .map(org.jetbrains.kotlin.gradle.dsl.KotlinVersion::fromVersion)
    .orNull
val kotlinApiVersionOverride = providers.gradleProperty("kotlin_api_version")
    .map(org.jetbrains.kotlin.gradle.dsl.KotlinVersion::fromVersion)
    .orNull
val kotlinAdditionalCliOptions = providers.gradleProperty("kotlin_additional_cli_options")
    .map { it.split(" ") }
    .orNull

repositories {
    mavenCentral()
    kotlin_repo_url?.also { maven(it) }
}

kotlin {
    wasmWasi {
        nodejs()
        binaries.executable()

        compilations.configureEach {
            compileTaskProvider.configure {
                if (kotlinLanguageVersionOverride != null) {
                    compilerOptions {
                        languageVersion.set(kotlinLanguageVersionOverride)
                        logger.info("<KUP> ${this@configure.path} : set LV to $kotlinLanguageVersionOverride")
                    }
                }
                if (kotlinApiVersionOverride != null) {
                    compilerOptions {
                        apiVersion.set(kotlinApiVersionOverride)
                        logger.info("<KUP> ${this@configure.path} : set APIV to $kotlinApiVersionOverride")
                    }
                }
                if (kotlinAdditionalCliOptions != null) {
                    compilerOptions {
                        freeCompilerArgs.addAll(kotlinAdditionalCliOptions)
                        logger.info(
                            "<KUP> ${this@configure.path} : added ${
                                kotlinAdditionalCliOptions.joinToString(
                                    " "
                                )
                            }"
                        )
                    }
                }
                compilerOptions {
                    // output reported warnings even in the presence of reported errors
                    freeCompilerArgs.add("-Xreport-all-warnings")
                    logger.info("<KUP> ${this@configure.path} : added -Xreport-all-warnings")
                    // output kotlin.git-searchable names of reported diagnostics
                    freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
                    logger.info("<KUP> ${this@configure.path} : added -Xrender-internal-diagnostic-names")
                }
            }
        }
    }

    sourceSets {
        wasmWasiTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}