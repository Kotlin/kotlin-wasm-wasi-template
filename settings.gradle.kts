rootProject.name = "kotlin-wasm-wasi-example"

pluginManagement {
    val kotlin_repo_url: String? by settings

    resolutionStrategy {
        repositories {
            gradlePluginPortal()

            kotlin_repo_url?.also { maven(it) }

            mavenLocal()
        }
    }
}

dependencyResolutionManagement {
    val kotlin_version: String? by settings

    versionCatalogs {
        create("libs") {
            kotlin_version?.let {
                version("kotlin", it)
            }
        }
    }
}

