plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
    id("com.android.library")
    id("maven-publish")
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
            // Removed dependsOn(commonMain) to comply with Default Kotlin Hierarchy Template
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

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.isroot"
            artifactId = "lmbridge"
            version = project.version.toString()
            
            // KMP publishing usually handles artifacts automatically via the kotlin-multiplatform plugin
            // but we define the publication name as 'release' to match the Action's expectations.
        }
    }
}
