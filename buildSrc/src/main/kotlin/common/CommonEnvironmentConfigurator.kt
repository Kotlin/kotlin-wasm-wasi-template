/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package common

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.JsEnvironmentConfigurator
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrSubTarget.Companion.RUN_TASK_NAME

@ExperimentalWasmDsl
class CommonEnvironmentConfigurator(
    private val subTargetSpecific: KotlinCommonSubTarget,
) : JsEnvironmentConfigurator<Exec>(subTargetSpecific) {

    override fun configureBinaryRun(binary: JsIrBinary): TaskProvider<Exec> {
        val binaryRunName = subTarget.disambiguateCamelCased(
            binary.mode.name.lowercase(),
            RUN_TASK_NAME
        )

        return project.tasks.register(binaryRunName, Exec::class.java).also {
            it.configure {
                dependsOn(subTargetSpecific.setupTask)

                group = subTargetSpecific.name

                description = "Run ${subTargetSpecific.name}"

                dependsOn(binary.linkTask)

                val binaryInputFile =
                    binary.mainFile.map { it.asFile.parentFile.resolve(it.asFile.nameWithoutExtension + ".wasm") }
                val workingDir = binaryInputFile.map { it.parentFile.parentFile }

                val executableStr = subTargetSpecific.envSpec.executable

                val args = subTargetSpecific.runArgs

                doFirst {
                    executable = executableStr.get()

                    val isolationDir = workingDir.get().resolve("static/$name").also {
                        it.mkdirs()
                    }

                    setWorkingDir(isolationDir)

                    args(
                        args.get()(
                            isolationDir.toPath(),
                            binaryInputFile.get().toPath()
                        )
                    )
                }

            }
        }
    }
}