package common/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrSubTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import java.nio.file.Path
import javax.inject.Inject

abstract class KotlinCommonSubTarget
@Inject
constructor(
    target: KotlinJsIrTarget,
    name: String,
    val version: String,
) : KotlinJsIrSubTarget(target, name) {
    val os: Provider<String> = project.providers.systemProperty("os.name")
    val arch: Provider<String> = project.providers.systemProperty("os.arch")

    val envSpec = project.extensions.create(
        "${name}EnvSpec",
        CommonEnvSpec::class.java,
        name,
    ).apply {
        installationDirectory.convention(
            project.objects.directoryProperty().fileValue(
                project.gradle.gradleUserHomeDir.resolve("wasm-tools")
            )
        )

        download.convention(false)

        version.convention(this@KotlinCommonSubTarget.version)
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
            this.archiveOperation.set(this@KotlinCommonSubTarget.archiveOperation)
            this.os.set(this@KotlinCommonSubTarget.os)
            this.arch.set(this@KotlinCommonSubTarget.arch)
            this.version.set(this@KotlinCommonSubTarget.version)
        }
    }

    val runArgs: Property<(Path, Path) -> List<String>> = project.objects
        .property<(Path, Path) -> List<String>>()
        .convention { _, _ ->
            emptyList()
        }

    val testArgs: Property<(Path, Path) -> List<String>> = project.objects
        .property<(Path, Path) -> List<String>>()
        .convention { _, _ ->
            emptyList()
        }

    val archiveOperation: Property<(ArchiveOperations, Path) -> Any> = project.objects.property()

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
            argsProperty.set(this@KotlinCommonSubTarget.testArgs)
        }
    }
}