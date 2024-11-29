package common

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.targets.js.AbstractSetupTask
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault
abstract class CommonSetupTask @Inject constructor(
    private val settings: CommonEnvSpec,
) : AbstractSetupTask<CommonEnv, CommonEnvSpec>(settings) {

    @get:Internal
    override val artifactPattern: String
        get() = "/"

    @get:Internal
    override val artifactModule: String = settings.moduleGroup

    @get:Internal
    override val artifactName: String
        get() = settings.name

    @get:Internal
    abstract val fileCollectionSources: Property<(File) -> Any>

    @get:Inject
    abstract val fs: FileSystemOperations

    @get:Internal
    abstract val extractionAction: Property<(Provider<OsType>, File?, String) -> Unit>

    @Internal
    val osType = settings.currentOsType

    @Internal
    val version = settings.version

    override fun extract(archive: File) {
        fs.copy {
            from(
                fileCollectionSources.get()(archive)
            )
            into(destination)
        }

        extractionAction.get()(osType, destination, version.get())
    }
}
