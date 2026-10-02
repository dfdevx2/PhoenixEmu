pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PhoenixEmu"
include(":app")

// ---------------------------------------------------------------------
// Modulo nativo, ligado na Fase 4.
//
// Exige no SDK Manager: NDK (side by side) e CMake 3.22.1. Sem eles o build
// inteiro quebra -- inclusive o app.
// ---------------------------------------------------------------------
include(":emulator")
