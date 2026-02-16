plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.squintboyadvance.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 30

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DLIBMGBA_ONLY=ON",
                    "-DBUILD_STATIC=ON",
                    "-DBUILD_SHARED=OFF",
                    "-DDISABLE_FRONTENDS=ON",
                    "-DBUILD_GL=OFF",
                    "-DBUILD_GLES2=OFF",
                    "-DBUILD_GLES3=OFF",
                    "-DUSE_EPOXY=OFF",
                    "-DBUILD_QT=OFF",
                    "-DBUILD_SDL=OFF",
                    "-DBUILD_LIBRETRO=OFF",
                    "-DUSE_FFMPEG=OFF",
                    "-DUSE_PNG=ON",
                    "-DUSE_ZLIB=ON",
                    "-DUSE_SQLITE3=OFF",
                    "-DUSE_DISCORD_RPC=OFF",
                    "-DENABLE_SCRIPTING=OFF",
                    "-DUSE_EDITLINE=OFF",
                    "-DM_CORE_GBA=ON",
                    "-DM_CORE_GB=ON",
                    "-DENABLE_DEBUGGERS=OFF",
                    "-DENABLE_GDB_STUB=OFF"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
}
