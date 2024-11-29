package common

import org.jetbrains.kotlin.gradle.targets.js.AbstractEnv
import org.jetbrains.kotlin.gradle.tasks.internal.CleanableStore
import java.io.File

data class CommonEnv(
    override val download: Boolean,
    override val cleanableStore: CleanableStore,
    override val dir: File,
    override val executable: String,
    override val ivyDependency: String,
    override val downloadBaseUrl: String?,
) : AbstractEnv
