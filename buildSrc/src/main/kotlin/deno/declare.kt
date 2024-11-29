package deno

import common.*
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import java.io.File

fun KotlinJsIrTarget.deno() {
    subTargets.add(
        project.objects.newInstance(
            KotlinCommonSubTarget::class.java,
            this,
            "deno",
            "1.38.3",
            { os: Provider<OsType>, version: String ->
                val denoSuffix = when (os.get()) {
                    OsType(OsName.LINUX, OsArch.X86_64) -> "x86_64-unknown-linux-gnu"
                    OsType(OsName.MAC, OsArch.X86_64) -> "x86_64-apple-darwin"
                    OsType(OsName.MAC, OsArch.ARM64) -> "aarch64-apple-darwin"
                    else -> null
                }

                "https://github.com/denoland/deno/releases/download/v$version/deno-$denoSuffix.zip"
            },
            { installedDir: File?, _: Provider<OsType>, _: String ->
                installedDir?.resolve("deno")?.normalize()?.absolutePath ?: "deno"
            }
        ).also {
            it.configure()
            it.getArgs.set { workingDir, fileName, inputWasmFile ->
                val denoMjs = prepareFile(
                    workingDir,
                    fileName,
                    inputWasmFile
                )

                denoArgs(denoMjs)
            }
            it.archiveOperation.set(project.serviceOf<ArchiveOperations>()::zipTree)
            it.subTargetConfigurators.add(CommonEnvironmentConfigurator(it, it.envSpec))
        }
    )
}