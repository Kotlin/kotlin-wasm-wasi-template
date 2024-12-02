package edge

import dsl.runtime
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmWasiTargetDsl
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun KotlinWasmWasiTargetDsl.wasmEdge() {
    runtime(
        "wasmEdge",
        "0.14.0"
    ) {
        download { os: String, arch: String, version: String ->
            val artifactName = artifactFileName(os, arch, version)

            "https://github.com/WasmEdge/WasmEdge/releases/download/$version/$artifactName"
        }

        extractAction { os: String, _: String, version: String, installationDir: Path? ->
            setOf("mac", "linux").none {
                os.lowercase().contains(it)
            }.takeIf { it }?.let { return@extractAction }

            val libDirectory = installationDir!!.resolve(artifactDir(os, version))
                .resolve(if (os.lowercase().contains("mac")) "lib" else "lib64")

            val targets = if (os.lowercase().contains("mac"))
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

        executable { os: String, arch: String, version: String, installationDir: Path? ->
            installationDir!!.resolve(artifactDir(os, version)).resolve("bin/wasmedge").absolutePathString()
        }

        runArgs { _: Path, entry: Path ->
            listOf(
                "--enable-gc",
                "--enable-exception-handling",
                entry.normalize().absolutePathString(),
                "dummy"
            )
        }

        testArgs { _: Path, entry: Path ->
            listOf(
                "--enable-gc",
                "--enable-exception-handling",
                entry.normalize().absolutePathString(),
                "startUnitTests"
            )
        }
    }
}

fun artifactFileName(os: String, arch: String, version: String): String {
    val wasmEdgeSuffix = when {
        os.lowercase().contains("linux") && arch == "x86_64" -> "manylinux_2_28_x86_64.tar.gz"
        os.lowercase().contains("mac") && arch == "x86_64" -> "darwin_x86_64.tar.gz"
        os.lowercase().contains("mac") && (arch == "arm" || arch.startsWith("aarch")) -> "darwin_arm64.tar.gz"
        os.lowercase().contains("windows") -> "windows.zip"

        else -> error("unsupported os type ${os} and arch $arch")
    }

    return "WasmEdge-$version-$wasmEdgeSuffix"
}

fun artifactDir(os: String, version: String): String {
    val wasmEdgeInnerSuffix = when {
        os.lowercase().contains("linux") -> "Linux"
        os.lowercase().contains("mac") -> "Darwin"
        os.lowercase().contains("windows") -> "Windows"
        else -> error("unsupported os type $os")
    }

    return "WasmEdge-$version-$wasmEdgeInnerSuffix"
}