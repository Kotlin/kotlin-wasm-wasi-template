rootProject.name = "kotlin-wasm-wasi-template"

pluginManagement {
    val kotlin_repo_url: String? by settings

    repositories {
        gradlePluginPortal()

        kotlin_repo_url?.also { maven(it) }
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

