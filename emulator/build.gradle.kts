plugins {
    alias(libs.plugins.android.library)
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

        // O emulador nao roda em x86; limitar aqui corta tempo de build.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            // Os nucleos sao carregados com dlopen em tempo de execucao, entao
            // precisam existir como arquivo e nao podem ser comprimidos no APK.
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        compose = true
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
}
