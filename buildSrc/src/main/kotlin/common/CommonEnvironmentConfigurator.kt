/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package common

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.JsEnvironmentConfigurator
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrSubTarget.Companion.RUN_TASK_NAME

@ExperimentalWasmDsl
class CommonEnvironmentConfigurator(
    private val subTargetSpecific: KotlinCommonSubTarget,
    private val envSpec: CommonEnvSpec,
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

                description = "Run"

                dependsOn(binary.linkTask)

                val binaryInputFile = binary.mainFile.map { it.asFile.parentFile.resolve(it.asFile.nameWithoutExtension + ".wasm") }
                val workingDir = binaryInputFile.map { it.parentFile.parentFile }

                val executableStr = envSpec.executable

                val subTargetName = subTargetSpecific.name
                val args = subTargetSpecific.getArgs

                doFirst {
                    executable = executableStr.get()

                    setWorkingDir(workingDir)

                    args(
                        args.get()(
                            workingDir.get(),
                            "start${subTargetName.capitalized()}.mjs",
                            binaryInputFile.get()
                        )
                    )
                }

            }
        }
    }
}