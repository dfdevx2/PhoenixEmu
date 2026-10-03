plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// A chave da RAWG nunca fica no codigo nem no repositorio.
// Coloque em ~/.gradle/gradle.properties (fora do projeto, nunca commitado):
//     RAWG_API_KEY=suachaveaqui
// Se estiver ausente, o app simplesmente nao usa o fallback da RAWG.
val rawgApiKey: String = providers.gradleProperty("RAWG_API_KEY").orElse("").get()

android {
    namespace = "com.dfdx047.phoenixemu"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dfdx047.phoenixemu"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "RAWG_API_KEY", "\"$rawgApiKey\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            // Fase 0: R8 LIGADO. Compose sem R8 e mensuravelmente mais lento,
            // e qualquer medicao de fluidez feita em debug nao vale nada.
            optimization {
                enable = true
            }
            // Se o DSL do AGP 9 aceitar encolhimento de recursos neste bloco,
            // descomente. Se reclamar, deixe comentado: o ganho e pequeno
            // comparado ao R8 de codigo.
            // isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Os nucleos libretro sao abertos com dlopen por CAMINHO de arquivo.
        // Isto faz o Android extrair as .so para `nativeLibraryDir` na
        // instalacao. Tem de ficar aqui, no modulo do APP: no modulo de
        // biblioteca esta opcao nao chega ao APK final.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

ksp {
    // O schema exportado fica versionado no Git. E ele que torna uma migracao
    // futura revisavel: da para ver em diff exatamente o que mudou na tabela.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Fase 4: o emulador nativo, que roda no processo separado :emu.
    implementation(project(":emulator"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // AndroidX
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // documentfile saiu: o RomScanner usa DocumentsContract direto.
    // A entrada continua no catalogo se voce precisar dele na Fase 1.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // Imagens
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Rede / JSON
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
