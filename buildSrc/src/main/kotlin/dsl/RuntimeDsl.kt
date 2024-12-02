package dsl

import common.CommonEnvironmentConfigurator
import common.KotlinCommonSubTarget
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmWasiTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import java.nio.file.Path

fun KotlinWasmWasiTargetDsl.runtime(
    name: String,
    version: String,
    configure: WasmWasiRuntimeDsl.() -> Unit
): KotlinCommonSubTarget {
    this as KotlinJsIrTarget
    return project.objects.newInstance(
        KotlinCommonSubTarget::class.java,
        this,
        name,
        version,
    ).also {
        it.configure()
        it.subTargetConfigurators.add(CommonEnvironmentConfigurator(it))
        WasmWasiRuntimeImpl(it).configure()

        subTargets.add(it)
    }
}

interface WasmWasiRuntimeDsl {
    val extension: Property<String>

    fun download(
        configure: (
            os: String,
            arch: String,
            version: String
        ) -> String
    )

    fun archiveOperation(
        configure: (
            archiveOperations: ArchiveOperations,
            entry: Path,
        ) -> Any
    )

    fun extractAction(
        configure: (
            os: String,
            arch: String,
            version: String,
            installationDir: Path?
        ) -> Unit
    )

    fun executable(
        configure: (
            os: String,
            arch: String,
            version: String,
            installationDir: Path?
        ) -> String
    )

    fun runArgs(
        configure: (
            isolationDir: Path,
            entry: Path
        ) -> List<String>
    )

    fun testArgs(
        configure: (
            isolationDir: Path,
            entry: Path,
        ) -> List<String>
    )
}

class WasmWasiRuntimeImpl(
    private val subTarget: KotlinCommonSubTarget
) : WasmWasiRuntimeDsl {
    private val os: Provider<String> = subTarget.os
    private val arch: Provider<String> = subTarget.arch
    private val versionValue
        get() = subTarget.version

    override val extension: Property<String> = subTarget.project.objects.property(String::class.java)

    override fun download(configure: (os: String, arch: String, version: String) -> String) {
        subTarget.envSpec.download.set(true)
        subTarget.envSpec.downloadBaseUrl.set(
            os.zip(arch) { osValue, archValue ->
                configure(osValue, archValue, versionValue)
            }
        )
    }

    override fun archiveOperation(
        configure: (
            archiveOperations: ArchiveOperations,
            entry: Path,
        ) -> Any
    ) {
        subTarget.archiveOperation.set(configure)
    }

    override fun extractAction(configure: (os: String, arch: String, version: String, installationDir: Path?) -> Unit) {
        subTarget.setupTask.configure {
            this.extractionAction.set(configure)
        }
    }

    override fun executable(configure: (os: String, arch: String, version: String, installationDir: Path?) -> String) {
        subTarget.envSpec.executableCommand.set(
            os.zip(arch) { osValue, archValue ->
                { dir: Path ->
                    configure(osValue, archValue, versionValue, dir)
                }
            }
        )
    }

    override fun runArgs(configure: (isolationDir: Path, entry: Path) -> List<String>) {
        subTarget.runArgs.set(configure)
    }

    override fun testArgs(configure: (isolationDir: Path, entry: Path) -> List<String>) {
        subTarget.testArgs.set(configure)
    }

}