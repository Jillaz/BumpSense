pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // ✅ Репозиторий RuStore SDK (ставим ПЕРЕД MapLibre для приоритета)
        maven {
            url = uri("https://artifactory-external.vkpartner.ru/artifactory/maven")
        }

        maven {
            url = uri("https://maven.maplibre.org")
        }
    }
}

rootProject.name = "BumpSense"
include(":app")