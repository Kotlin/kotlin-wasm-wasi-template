package common

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.targets.js.AbstractSetupTask
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.name

@DisableCachingByDefault
abstract class CommonSetupTask @Inject constructor(
    settings: CommonEnvSpec,
) : AbstractSetupTask<CommonEnv, CommonEnvSpec>(settings) {

    @get:Internal
    override val artifactPattern: String
        get() = "/"

    @get:Internal
    override val artifactModule: String = settings.moduleGroup

    @get:Internal
    override val artifactName: String = settings.name

    @get:Inject
    abstract val fs: FileSystemOperations

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Internal
    abstract val extractionAction: Property<(String, String, String, Path?) -> Unit>

    @get:Internal
    abstract val os: Property<String>

    @get:Internal
    abstract val arch: Property<String>

    @get:Internal
    abstract val version: Property<String>

    @get:Internal
    abstract val archiveOperation: Property<(ArchiveOperations, Path) -> Any>

    override fun extract(archive: File) {
        val archiveOperationValue: (ArchiveOperations, Path) -> Any = archiveOperation.getOrElse { ao, path ->
            when {
                path.name.endsWith(".tar.gz") -> ao.tarTree(path)
                path.name.endsWith(".zip") -> ao.zipTree(path)
                else -> error("Can't detect archive type for $path. Set archiveOperation.")
            }
        }

        fs.copy {
            from(
                archiveOperationValue(archiveOperations, archive.toPath())
            )
            into(destination)
        }

        extractionAction.get()(os.get(), arch.get(), version.get(), destination.toPath())
    }
}
