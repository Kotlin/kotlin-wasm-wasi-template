@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.internal.os.OperatingSystem
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.internal.file.archive.compression.*
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport
import java.io.*
import java.net.*
import java.nio.file.Files
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.undercouchDownload) apply false
}

buildscript {
    dependencies {
        // to extract `tar.xz`
        classpath("org.tukaani:xz:1.9")
    }
}

repositories {
    mavenCentral()
}

kotlin {
    wasmWasi {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        wasmWasiTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Uncomment following block to turn off using the Exception Handling proposal.
// Note, with this option, the compiler will generate `unreachable` instruction instead of throw, 
// and a Wasm module will stop execution in this case.
//
// tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile>().configureEach {
//     compilerOptions.freeCompilerArgs.addAll(listOf("-Xwasm-use-traps-instead-of-exceptions"))
// }

// Uncomment following block to force using the final version of the Exception Handling proposal.
// Note, the new opcodes are not supported yet in WAMR and Node.js
//
// tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile>().configureEach {
//     compilerOptions.freeCompilerArgs.addAll(listOf("-Xwasm-use-new-exception-proposal"))
// }

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

// Deno tasks
val unzipDeno = run {
    val denoVersion = "1.46.3"
    val denoDirectory = "https://github.com/denoland/deno/releases/download/v$denoVersion"
    val denoSuffix = when (currentOsType) {
        OsType(OsName.LINUX, OsArch.X86_64) -> "x86_64-unknown-linux-gnu"
        OsType(OsName.MAC, OsArch.X86_64) -> "x86_64-apple-darwin"
        OsType(OsName.MAC, OsArch.ARM64) -> "aarch64-apple-darwin"
        else -> return@run null
    }
    val denoLocation = "$denoDirectory/deno-$denoSuffix.zip"

    val downloadedTools = File(layout.buildDirectory.asFile.get(), "tools")

    val downloadDeno = tasks.register("denoDownload", Download::class) {
        src(denoLocation)
        dest(File(downloadedTools, "deno-$denoVersion-$denoSuffix.zip"))
        overwrite(false)
    }

    tasks.register("denoUnzip", Copy::class) {
        dependsOn(downloadDeno)
        from(zipTree(downloadDeno.get().dest))
        val unpackedDir = File(downloadedTools, "deno-$denoVersion-$denoSuffix")
        into(unpackedDir)
    }
}

fun getDenoExecutableText(wasmFileName: String): String = """
import Context from "https://deno.land/std@0.201.0/wasi/snapshot_preview1.ts";

const context = new Context({
  args: Deno.args,
  env: Deno.env.toObject(),
});

const binary = await Deno.readFile("./$wasmFileName");
const module = await WebAssembly.compile(binary);
const wasmInstance = await WebAssembly.instantiate(module, {
  "wasi_snapshot_preview1": context.exports,
});

context.initialize(wasmInstance);
wasmInstance.exports.startUnitTests?.();
"""

fun Project.createDenoExecutableFile(
    taskName: String,
    wasmFileName: Provider<String>,
    outputDirectory: Provider<File>,
    resultFileName: String,
): TaskProvider<Task> = tasks.register(taskName, Task::class) {
    val denoMjs = outputDirectory.map { it.resolve(resultFileName) }
    inputs.property("wasmFileName", wasmFileName)
    outputs.file(denoMjs)

    doFirst {
        denoMjs.get().writeText(getDenoExecutableText(wasmFileName.get()))
    }
}

fun Project.createDenoExec(
    nodeMjsFile: RegularFileProperty,
    taskName: String,
    taskGroup: String?
): TaskProvider<Exec> {
    val denoFileName = "runUnitTestsDeno.mjs"

    val outputDirectory = nodeMjsFile.map { it.asFile.parentFile }
    val wasmFileName = nodeMjsFile.map { "${it.asFile.nameWithoutExtension}.wasm" }

    val denoFileTask = createDenoExecutableFile(
        taskName = "${taskName}CreateDenoFile",
        wasmFileName = wasmFileName,
        outputDirectory = outputDirectory,
        resultFileName = denoFileName
    )

    return tasks.register(taskName, Exec::class) {
        if (unzipDeno != null) {
            dependsOn(unzipDeno)
        }
        dependsOn(denoFileTask)

        taskGroup?.let {
            group = it
        }

        description = "Executes tests with Deno"

        val newArgs = mutableListOf<String>()

        executable = when (currentOsType.name) {
            OsName.WINDOWS -> "deno.exe"
            else -> unzipDeno?.let { File(unzipDeno.get().destinationDir, "deno").absolutePath } ?: "deno"
        }

        newArgs.add("run")
        newArgs.add("--v8-flags=--experimental-wasm-exnref")
        newArgs.add("--allow-read")
        newArgs.add("--allow-env")

        newArgs.add(denoFileName)

        args(newArgs)
        doFirst {
            workingDir(outputDirectory)
        }
    }
}


tasks.withType<KotlinJsTest>().all {
    val denoExecTask = createDenoExec(
        inputFileProperty,
        name.replace("Node", "Deno"),
        group
    )

    denoExecTask.configure {
        dependsOn (
            project.provider { this@all.taskDependencies }
        )
    }

    tasks.withType<KotlinTestReport> {
        dependsOn(denoExecTask)
    }
}

tasks.withType<NodeJsExec>().all {
    val denoExecTask = createDenoExec(
        inputFileProperty,
        name.replace("Node", "Deno"),
        group
    )

    denoExecTask.configure {
        dependsOn (
            project.provider { this@all.taskDependencies }
        )
    }
}

// WasmEdge tasks
val wasmEdgeVersion = "0.16.0"

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
                listOf("libwasmedge.0.1.0.dylib", "libwasmedge.0.1.0.tbd")
            else listOf("libwasmedge.so.0.1.0")

            targets.forEach {
                val target = libDirectory.resolve(it)
                val firstLink = libDirectory.resolve(it.replace("0.1.0", "0")).also(Files::deleteIfExists)
                val secondLink = libDirectory.resolve(it.replace(".0.1.0", "")).also(Files::deleteIfExists)

                Files.createSymbolicLink(firstLink, target)
                Files.createSymbolicLink(secondLink, target)
            }
        }
    }
}

fun Project.createWasmEdgeExec(
    nodeMjsFile: RegularFileProperty,
    taskName: String,
    taskGroup: String?,
    startFunction: String
): TaskProvider<Exec> {
    val outputDirectory = nodeMjsFile.map { it.asFile.parentFile }
    val wasmFileName = nodeMjsFile.map { "${it.asFile.nameWithoutExtension}.wasm" }

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
        name.replace("Node", "WasmEdge"),
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

tasks.withType<NodeJsExec>().all {
     val wasmEdgeRunTask = createWasmEdgeExec(
        inputFileProperty,
        name.replace("Node", "WasmEdge"),
        group,
        "dummy"
    )

    wasmEdgeRunTask.configure {
        dependsOn (
            project.provider { this@all.taskDependencies }
        )
    }
}

// Wasmtime tasks
val wasmtimeVersion = "dev" // only `dev` supports GC

val wasmtimeSuffix = when (currentOsType) {
    OsType(OsName.LINUX, OsArch.X86_64)   -> "x86_64-linux"
    OsType(OsName.LINUX, OsArch.ARM64)    -> "aarch64-linux"
    OsType(OsName.MAC, OsArch.X86_64)     -> "x86_64-macos"
    OsType(OsName.MAC, OsArch.ARM64)      -> "aarch64-macos"
    OsType(OsName.WINDOWS, OsArch.X86_32),
    OsType(OsName.WINDOWS, OsArch.X86_64) -> "x86_64-windows"

    else                                  -> error("unsupported os type $currentOsType")
}

val wasmtimeArtifactName = "wasmtime-$wasmtimeVersion-$wasmtimeSuffix"

val unzipWasmtime = run {
    val wasmtimeDirectory = "https://github.com/bytecodealliance/wasmtime/releases/download/$wasmtimeVersion"
    val archiveType = if (currentOsType.name == OsName.WINDOWS) "zip" else "tar.xz"
    val wasmtimeArchiveName = "$wasmtimeArtifactName.$archiveType"
    val wasmtimeLocation = "$wasmtimeDirectory/$wasmtimeArchiveName"

    val downloadedTools = File(layout.buildDirectory.asFile.get(), "tools")

    val downloadWasmtime = tasks.register("wasmtimeDownload", Download::class) {
        src(wasmtimeLocation)
        dest(File(downloadedTools, wasmtimeArchiveName))
        overwrite(false)
    }

    tasks.register("wasmtimeUnzip", Copy::class) {
        dependsOn(downloadWasmtime)

        val archive = downloadWasmtime.get().dest

        from(if (archive.extension == "zip") zipTree(archive) else tarTree(XzArchiver(archive)))

        into(downloadedTools)
    }
}

private class XzArchiver(private val file: File) : CompressedReadableResource {
    override fun read(): InputStream = org.tukaani.xz.XZInputStream(file.inputStream().buffered())
    override fun getURI(): URI = URIBuilder(file.toURI()).schemePrefix("xz:").build()
    override fun getBackingFile(): File = file
    override fun getBaseName(): String = file.name
    override fun getDisplayName(): String = file.path
}

fun Project.createWasmtimeExec(
    nodeMjsFile: RegularFileProperty,
    taskName: String,
    taskGroup: String?,
    startFunction: String
): TaskProvider<Exec> {
    val outputDirectory = nodeMjsFile.map { it.asFile.parentFile }
    val wasmFileName = nodeMjsFile.map { "${it.asFile.nameWithoutExtension}.wasm" }

    return tasks.register(taskName, Exec::class) {
        dependsOn(unzipWasmtime)
        inputs.property("wasmFileName", wasmFileName)

        taskGroup?.let { group = it }

        description = "Executes tests with Wasmtime"

        val wasmtimeDirectory = unzipWasmtime.get().destinationDir.resolve(wasmtimeArtifactName)

        val executableName = when (currentOsType.name) {
            OsName.WINDOWS -> "wasmtime.exe"
            else           -> "wasmtime"
        }
        executable = wasmtimeDirectory.resolve(executableName).absolutePath

        doFirst {
            val newArgs = mutableListOf<String>()

            newArgs.add("-W")
            newArgs.add("function-references,gc")

            newArgs.add("-D")
            newArgs.add("logging=y")

            newArgs.add("--invoke")
            newArgs.add(startFunction)

            newArgs.add(wasmFileName.get())

            args(newArgs)
            workingDir(outputDirectory)

            // to show stacktraces
            environment("RUST_BACKTRACE", "full")
        }
    }
}

tasks.withType<KotlinJsTest>().all {
    val wasmtimeRunTask = createWasmtimeExec(
        inputFileProperty,
        name.replace("Node", "Wasmtime"),
        group,
        "startUnitTests"
    )

    wasmtimeRunTask.configure {
        dependsOn(
            project.provider { this@all.taskDependencies }
        )
    }

    tasks.withType<KotlinTestReport> {
        dependsOn(wasmtimeRunTask)
    }
}

tasks.withType<NodeJsExec>().all {
    val wasmtimeRunTask = createWasmtimeExec(
        inputFileProperty,
        name.replace("Node", "Wasmtime"),
        group,
        "dummy"
    )

    wasmtimeRunTask.configure {
        dependsOn(
            project.provider { this@all.taskDependencies }
        )
    }
}
