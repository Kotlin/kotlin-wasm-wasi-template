package common/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrSubTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import java.io.File
import java.util.*
import javax.inject.Inject

abstract class KotlinCommonSubTarget
@Inject
constructor(
    target: KotlinJsIrTarget,
    name: String,
    private val version: String,
    private val downloadUrl: (os: Provider<OsType>, version: String) -> String,
    commandCalc: (installedDir: File?, os: Provider<OsType>, version: String) -> String,
) : KotlinJsIrSubTarget(target, name) {
    val envSpec = project.extensions.create(
        "${name}EnvSpec",
        CommonEnvSpec::class.java,
        name,
        commandCalc,
    ).apply {
        val currentOsType: Provider<OsType> = run {
            val gradleOs = OperatingSystem.current()
            val osName = when {
                gradleOs.isMacOsX -> OsName.MAC
                gradleOs.isWindows -> OsName.WINDOWS
                gradleOs.isLinux -> OsName.LINUX
                else -> OsName.UNKNOWN
            }

            project.providers.systemProperty("sun.arch.data.model").zip(
                project.providers.systemProperty("os.arch")
            ) { sunArch, osArch ->
                val calculatedOsArch = when (sunArch) {
                    "32" -> OsArch.X86_32
                    "64" -> when (osArch.lowercase(Locale.getDefault())) {
                        "aarch64" -> OsArch.ARM64
                        else -> OsArch.X86_64
                    }

                    else -> OsArch.UNKNOWN
                }

                OsType(osName, calculatedOsArch)
            }
        }

        this.currentOsType.set(currentOsType)

        installationDirectory.convention(
            project.objects.directoryProperty().fileValue(
                project.gradle.gradleUserHomeDir.resolve("wasm-tools")
            )
        )

        download.convention(true)
        version.convention(this@KotlinCommonSubTarget.version)

        downloadBaseUrl.set(
            downloadUrl(currentOsType, this@KotlinCommonSubTarget.version)
        )
    }

    val extractionAction: Property<(Provider<OsType>, File?, String) -> Unit> = project.objects
        .property<(Provider<OsType>, File?, String) -> Unit>()
        .convention { _, _, _ ->

        }

    val setupTask: TaskProvider<CommonSetupTask> = project.tasks.register(
        "kotlin${name.capitalized()}Setup",
        CommonSetupTask::class.java,
        envSpec
    ).also {
        it.configure {
            configuration = ivyDependencyProvider.map { ivyDependency ->
                project.configurations.detachedConfiguration(project.dependencies.create(ivyDependency))
                    .also { conf -> conf.isTransitive = false }
            }
            fileCollectionSources.set(archiveOperation)
            this.extractionAction.set(this@KotlinCommonSubTarget.extractionAction)
        }
    }

    val getArgs: Property<(File, String, File) -> List<String>> = project.objects.property()

    val archiveOperation: Property<(File) -> Any> = project.objects.property()

    override val testTaskDescription: String
        get() = "Run all ${target.name} tests inside $name using the builtin test framework"

    override fun configureTestDependencies(test: KotlinJsTest, binary: JsIrBinary) {
        test.dependsOn(binary.linkTask)
        test.dependsOn(setupTask)
    }

    override fun binaryInputFile(binary: JsIrBinary): Provider<RegularFile> {
        TODO("1")
    }

    override fun binarySyncTaskName(binary: JsIrBinary): String {
        TODO("2")
    }

    override fun binarySyncOutput(binary: JsIrBinary): Provider<Directory> {
        TODO("3")
    }

    override fun configureDefaultTestFramework(test: KotlinJsTest) {
        test.testFramework = CommonKotlinWasmTestFramework(
            test,
            name
        ).apply {
            executable.set(envSpec.executable)
            getArgs.set(this@KotlinCommonSubTarget.getArgs)
        }
    }
}