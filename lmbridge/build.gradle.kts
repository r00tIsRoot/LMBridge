plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
}

android {
    namespace = "com.isroot.lmbridge"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    androidResources {
        noCompress.add("litertlm")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Google LiteRT-LM dependency - fixed version 0.10.0
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")
}

// Publishing configuration
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.isroot"
                artifactId = "lmbridge"
                version = "0.0.16"

                from(components["release"])

                // POM 정보
                pom {
                    name.set("LMBridge")
                    description.set("Google LiteRT-LM Android SDK for on-device LLM inference")
                    url.set("https://github.com/r00tIsRoot/LMBridge")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    developers {
                        developer {
                            id.set("r00tIsRoot")
                            name.set("r00tIsRoot")
                            email.set("dlwodnjs0727@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/r00tIsRoot/LMBridge.git")
                        developerConnection.set("scm:git:https://github.com/r00tIsRoot/LMBridge.git")
                        url.set("https://github.com/r00tIsRoot/LMBridge")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "local"
                url = uri("$buildDir/repo")
            }
            // GitHub Pages에 배포하려면 주석 해제
            // maven {
            //     name = "GitHubPages"
            //     url = uri("https://r00tisroot.github.io/packages/")
            // }
        }
    }
}
