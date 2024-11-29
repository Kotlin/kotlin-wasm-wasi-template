package common

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.targets.js.EnvSpec
import org.jetbrains.kotlin.gradle.tasks.internal.CleanableStore
import java.io.File

/**
 * Spec for Node.js - common JS and Wasm runtime.
 */
abstract class CommonEnvSpec(
    val name: String,
    val commandCalc: (installedDir: File?, os: Provider<OsType>, version: String) -> String,
) : EnvSpec<CommonEnv>() {

    abstract val currentOsType: Property<OsType>

    final override val env: Provider<CommonEnv> = produceEnv()

    override val executable: Provider<String> = env.map { it.executable }

    val moduleGroup = "org.jetbrains.kotlin.wasm.runtime"

    final override fun produceEnv(): Provider<CommonEnv> {
        return download.map { downloadValue ->
            val versionValue = version.get()

            val dirName = downloadBaseUrl.get().hashCode().toByte().toString(16)
            val cleanableStore = CleanableStore[installationDirectory.get().asFile.absolutePath]
            val dir = cleanableStore[dirName].use()

            val downloadUrl = downloadBaseUrl.orNull

            fun getIvyDependency(): String {
                return "$moduleGroup:$name:$versionValue@${downloadUrl?.substringAfterLast(".")}"
            }

            CommonEnv(
                download = downloadValue,
                cleanableStore = cleanableStore,
                dir = dir,
                executable = commandCalc(dir, currentOsType, versionValue),
                ivyDependency = getIvyDependency(),
                downloadBaseUrl = downloadUrl,
            )
        }
    }
}
