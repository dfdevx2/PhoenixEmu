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
// O modulo nativo esta DESLIGADO de proposito.
//
// Ele usa CMake, e sem o NDK instalado o build inteiro quebraria -- inclusive
// o app, que hoje compila. Ligue quando o NDK estiver no SDK Manager
// (NDK "side by side" + CMake 3.22.1) e voce for comecar a Fase 4:
//
//   1. descomente a linha abaixo
//   2. baixe o `libretro.h` oficial para emulator/src/main/cpp/
//   3. adicione ao app/build.gradle.kts: implementation(project(":emulator"))
//
// include(":emulator")
// ---------------------------------------------------------------------
