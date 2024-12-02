package deno

import dsl.runtime
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmWasiTargetDsl
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun KotlinWasmWasiTargetDsl.deno() {
    runtime(
        "deno",
        "1.38.3"
    ) {
        download { os: String, arch: String, version: String ->
            val denoSuffix = when {
                os.lowercase().contains("linux") && arch == "x86_64" -> "x86_64-unknown-linux-gnu"
                os.lowercase().contains("mac") && arch == "x86_64" -> "x86_64-apple-darwin"
                os.lowercase().contains("mac") && (arch == "arm" || arch.startsWith("aarch")) -> "aarch64-apple-darwin"
                else -> null
            }

            "https://github.com/denoland/deno/releases/download/v$version/deno-$denoSuffix.zip"
        }

        executable { _: String, _: String, _: String, installationDir: Path? ->
            installationDir?.resolve("deno")?.normalize()?.absolutePathString() ?: "deno"
        }

        runArgs { isolationDir: Path, entry: Path ->
            val denoMjs = prepareFile(
                isolationDir.toFile(),
                "startDeno.mjs",
                entry.toFile()
            )

            denoArgs(denoMjs)
        }

        testArgs { isolationDir: Path, entry: Path ->
            val denoMjs = prepareFile(
                isolationDir.toFile(),
                "runUnitTestsDeno.mjs",
                entry.toFile()
            )

            denoArgs(denoMjs)
        }
    }
}