package edge

import common.*
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import java.io.File
import java.nio.file.Files

fun artifactFileName(os: Provider<OsType>, version: String): String {
    val wasmEdgeSuffix = when (os.get()) {
        OsType(OsName.LINUX, OsArch.X86_64) -> "manylinux_2_28_x86_64.tar.gz"
        OsType(OsName.MAC, OsArch.X86_64) -> "darwin_x86_64.tar.gz"
        OsType(OsName.MAC, OsArch.ARM64) -> "darwin_arm64.tar.gz"
        OsType(OsName.WINDOWS, OsArch.X86_32),
        OsType(OsName.WINDOWS, OsArch.X86_64) -> "windows.zip"

        else -> error("unsupported os type ${os.get()}")
    }

    return "WasmEdge-$version-$wasmEdgeSuffix"
}

fun artifactDir(os: Provider<OsType>, version: String): String {
    val wasmEdgeSuffix = when (os.get()) {
        OsType(OsName.LINUX, OsArch.X86_64) -> "manylinux_2_28_x86_64.tar.gz"
        OsType(OsName.MAC, OsArch.X86_64) -> "Darwin"
        OsType(OsName.MAC, OsArch.ARM64) -> "Darwin"
        OsType(OsName.WINDOWS, OsArch.X86_32),
        OsType(OsName.WINDOWS, OsArch.X86_64) -> "windows.zip"

        else -> error("unsupported os type ${os.get()}")
    }

    return "WasmEdge-$version-$wasmEdgeSuffix"
}

fun KotlinJsIrTarget.wasmEdge() {
    subTargets.add(
        project.objects.newInstance(
            KotlinCommonSubTarget::class.java,
            this,
            "wasmEdge",
            "0.14.0",
            { os: Provider<OsType>, version: String ->
                val artifactName = artifactFileName(os, version)

                "https://github.com/WasmEdge/WasmEdge/releases/download/$version/$artifactName"
            },
            { installedDir: File?, os: Provider<OsType>, version: String ->
                installedDir!!.resolve(artifactDir(os, version)).resolve("bin/wasmedge").absolutePath
            }
        ).also {
            it.configure()
            it.extractionAction.set { currentOsType: Provider<OsType>, installedDir: File?, version: String ->
                val osName = currentOsType.get().name
                if (osName !in setOf(OsName.MAC, OsName.LINUX)) return@set

                val libDirectory = installedDir!!.resolve(artifactDir(currentOsType, version)).toPath()
                    .resolve(if (osName == OsName.MAC) "lib" else "lib64")

                val targets = if (osName == OsName.MAC)
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
            it.getArgs.set { workingDir, fileName, inputWasmFile ->
                val newArgs = mutableListOf<String>()

                newArgs.add("--enable-gc")
                newArgs.add("--enable-exception-handling")

                newArgs.add(inputWasmFile.normalize().absolutePath)
                newArgs.add("startUnitTests")

                newArgs
            }
            val zipTree = project.serviceOf<ArchiveOperations>()::zipTree
            val tarTree = project.serviceOf<ArchiveOperations>()::tarTree
            it.archiveOperation.set { archive ->
                if (archive.extension == "zip") zipTree(archive) else tarTree(archive)
            }
            it.subTargetConfigurators.add(CommonEnvironmentConfigurator(it, it.envSpec))
        }
    )
}