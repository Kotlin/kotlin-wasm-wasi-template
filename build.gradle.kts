@file:OptIn(ExperimentalWasmDsl::class)

import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.wasm.wasmtime.WasmtimeExec
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport
import org.gradle.internal.os.OperatingSystem
import java.nio.file.Files
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.undercouchDownload) apply false
}

repositories {
    mavenCentral()
}

kotlin {
    wasmWasi {
        wasmtime()
        binaries.executable()
    }

    sourceSets {
        wasmWasiTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

///////////////////////////////// WASM EDGE /////////////////////////////////////////////////////////

enum class OsName { WINDOWS, MAC, LINUX, UNKNOWN }
enum class OsArch { X86_32, X86_64, ARM64, UNKNOWN }
data class OsType(val name: OsName, val arch: OsArch)

val currentOsType = run {
    val gradleOs = OperatingSystem.current()
    val osName = when {
        gradleOs.isMacOsX -> OsName.MAC
        gradleOs.isWindows -> OsName.WINDOWS
        gradleOs.isLinux -> OsName.LINUX
        else -> OsName.UNKNOWN
    }

    val osArch = when (providers.systemProperty("sun.arch.data.model").get()) {
        "32" -> OsArch.X86_32
        "64" -> when (providers.systemProperty("os.arch").get().lowercase(Locale.getDefault())) {
            "aarch64" -> OsArch.ARM64
            else -> OsArch.X86_64
        }
        else -> OsArch.UNKNOWN
    }

    OsType(osName, osArch)
}

val wasmEdgeVersion = libs.versions.wasmEdge.get()

val wasmEdgeInnerSuffix = when (currentOsType.name) {
    OsName.LINUX -> "Linux"
    OsName.MAC -> "Darwin"
    OsName.WINDOWS -> "Windows"
    else -> error("unsupported os type $currentOsType")
}

val unzipWasmEdge = run {
    val wasmEdgeDirectory = "https://github.com/WasmEdge/WasmEdge/releases/download/$wasmEdgeVersion"
    val wasmEdgeSuffix = when (currentOsType) {
        OsType(OsName.LINUX, OsArch.X86_64) -> "manylinux_2_28_x86_64.tar.gz"
        OsType(OsName.MAC, OsArch.X86_64) -> "darwin_x86_64.tar.gz"
        OsType(OsName.MAC, OsArch.ARM64) -> "darwin_arm64.tar.gz"
        OsType(OsName.WINDOWS, OsArch.X86_32),
        OsType(OsName.WINDOWS, OsArch.X86_64) -> "windows.zip"
        else -> error("unsupported os type $currentOsType")
    }

    val artifactName = "WasmEdge-$wasmEdgeVersion-$wasmEdgeSuffix"
    val wasmEdgeLocation = "$wasmEdgeDirectory/$artifactName"

    val downloadedTools = File(layout.buildDirectory.asFile.get(), "tools")

    val downloadWasmEdge = tasks.register("wasmEdgeDownload", Download::class) {
        src(wasmEdgeLocation)
        dest(File(downloadedTools, artifactName))
        overwrite(false)
    }

    tasks.register("wasmEdgeUnzip", Copy::class) {
        dependsOn(downloadWasmEdge)

        val archive = downloadWasmEdge.get().dest

        val subfolder = "WasmEdge-$wasmEdgeVersion-$wasmEdgeInnerSuffix"

        from(if (archive.extension == "zip") zipTree(archive) else tarTree(archive))

        val currentOsTypeForConfigurationCache = currentOsType.name

        val unzipDirectory = downloadedTools.resolve(subfolder)

        into(unzipDirectory)

        doLast {
            if (currentOsTypeForConfigurationCache !in setOf(OsName.MAC, OsName.LINUX)) return@doLast

            val libDirectory = unzipDirectory.toPath()
                .resolve(if (currentOsTypeForConfigurationCache == OsName.MAC) "lib" else "lib64")

            val targets = if (currentOsTypeForConfigurationCache == OsName.MAC)
                listOf("libwasmedge.0.1.1.dylib", "libwasmedge.0.1.1.tbd")
            else listOf("libwasmedge.so.0.1.1")

            targets.forEach {
                val target = libDirectory.resolve(it)
                val firstLink = libDirectory.resolve(it.replace("0.1.1", "0")).also(Files::deleteIfExists)
                val secondLink = libDirectory.resolve(it.replace(".0.1.1", "")).also(Files::deleteIfExists)

                Files.createSymbolicLink(firstLink, target)
                Files.createSymbolicLink(secondLink, target)
            }
        }
    }
}

fun Project.createWasmEdgeExec(
    wasmFile: RegularFileProperty,
    taskName: String,
    taskGroup: String?,
    startFunction: String
): TaskProvider<Exec> {
    val outputDirectory = wasmFile.map { it.asFile.parentFile }
    val wasmFileName = wasmFile.map { "${it.asFile.nameWithoutExtension}.wasm" }

    return tasks.register(taskName, Exec::class) {
        dependsOn(unzipWasmEdge)
        inputs.property("wasmFileName", wasmFileName)

        taskGroup?.let { group = it }

        description = "Executes tests with WasmEdge"

        val wasmEdgeDirectory = unzipWasmEdge.get().destinationDir

        executable = wasmEdgeDirectory.resolve("bin/wasmedge").absolutePath

        doFirst {
            val newArgs = mutableListOf<String>()

            newArgs.add(wasmFileName.get())
            newArgs.add(startFunction)

            args(newArgs)
            workingDir(outputDirectory)
        }
    }
}

tasks.withType<KotlinJsTest>().all {
    val wasmEdgeRunTask = createWasmEdgeExec(
        inputFileProperty,
        name.replace("Wasmtime", "WasmEdge"),
        group,
        "startUnitTests"
    )

    wasmEdgeRunTask.configure {
        dependsOn (
            project.provider { this@all.taskDependencies }
        )
    }

    tasks.withType<KotlinTestReport> {
        dependsOn(wasmEdgeRunTask)
    }
}

tasks.withType<WasmtimeExec>().all {
    val wasmEdgeRunTask = createWasmEdgeExec(
        inputFileProperty,
        name.replace("Wasmtime", "WasmEdge"),
        group,
        "dummy"
    )

    wasmEdgeRunTask.configure {
        dependsOn (
            project.provider { this@all.taskDependencies }
        )
    }
}


// Uncomment the following block to turn off using the Exception Handling proposal.
// Note, with this option, the compiler will generate `unreachable` instruction instead of throw,
// and a Wasm module will stop execution in this case.
//
//tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile>().configureEach {
//    compilerOptions.freeCompilerArgs.addAll(listOf("-Xwasm-use-traps-instead-of-exceptions"))
//}

// Uncomment the following block to force using the old version of the Exception Handling proposal.
//
//tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile>().matching { it.name.contains("wasmWasi", ignoreCase = true) }.configureEach {
//    compilerOptions.freeCompilerArgs.addAll(listOf("-Xwasm-use-new-exception-proposal=false"))
//}
