@file:OptIn(ExperimentalWasmDsl::class)

import deno.deno
import edge.wasmEdge
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id(libs.plugins.kotlinMultiplatform.get().pluginId)
    alias(libs.plugins.undercouchDownload) apply false
}

repositories {
    mavenCentral()

    mavenLocal()
}

kotlin {
    wasmWasi {
        nodejs()
        binaries.executable()
        deno()
        wasmEdge()
    }

    sourceSets {
        val wasmWasiTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}