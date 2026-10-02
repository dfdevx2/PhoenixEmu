plugins {
    alias(libs.plugins.android.library)
    // Sem este plugin o modulo nao compila: desde o Kotlin 2.0 o compilador
    // do Compose e um plugin separado, e `compose = true` sozinho nao basta.
    // Passou despercebido porque este modulo nunca tinha sido construido.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dfdx047.phoenixemu.emulator"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26

        externalNativeBuild {
            cmake {
                // c++_shared e obrigatorio quando ha mais de uma .so no processo:
                // com c++_static cada biblioteca levaria sua propria copia da
                // runtime e os nucleos libretro quebrariam de formas criativas.
                arguments += listOf("-DANDROID_STL=c++_shared")
                cppFlags += listOf("-std=c++17", "-fno-exceptions", "-fno-rtti")
            }
        }

        // So arm64. O Odin 3 e o Snapdragon 665 sao 64 bits, a Play Store exige
        // 64 bits, e cada ABI a mais dobra o tempo do build nativo -- que vai
        // rodar a cada etapa da Fase 4.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // A extracao das .so para dlopen e configurada no app/build.gradle.kts:
    // opcao de empacotamento so vale no modulo que gera o APK.

    buildFeatures {
        compose = true
        // Oboe chega como pacote prefab: e isto que deixa o CMake achar com
        // find_package(oboe REQUIRED CONFIG).
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.oboe)
}
