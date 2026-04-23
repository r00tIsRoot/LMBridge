plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
    id("com.android.library")
}

version = "0.1.1"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()
    js(IR) {
        browser()
    }
    iosX64 {
        compilations.getByName("main") {
            cinterops {
                val litert_lm by creating {
                    definitionFile.set(project.file("src/nativeInterop/cinterop/litert_lm.def"))
                }
            }
        }
    }
    iosArm64 {
        compilations.getByName("main") {
            cinterops {
                val litert_lm by creating {
                    definitionFile.set(project.file("src/nativeInterop/cinterop/litert_lm.def"))
                }
            }
        }
    }
    iosSimulatorArm64 {
        compilations.getByName("main") {
            cinterops {
                val litert_lm by creating {
                    definitionFile.set(project.file("src/nativeInterop/cinterop/litert_lm.def"))
                }
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val androidMain by getting {
            dependencies {
                // SDK 의존성 제거: 네이티브 라이브러리(lmbridge_core)를 직접 사용함
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.google.ai.edge.litertlm:litertlm-jvm:0.10.2")
            }
        }
        val jsMain by getting {
            dependencies {
                // JS interop with @litertjs/core will be handled via external declarations
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
        }
    }
}

android {
    namespace = "com.isroot.lmbridge.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
