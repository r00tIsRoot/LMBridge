plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
    id("com.android.library")
    id("maven-publish")
}

version = "0.1.2"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()
    js(IR) {
        browser()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val androidMain by getting
        val jvmMain by getting {
            dependencies {
                implementation("com.google.ai.edge.litertlm:litertlm-jvm:0.10.2")
            }
        }
        val jsMain by getting
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

// JVM sources jar
val jvmSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("jvmMain"))
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.isroot"
            artifactId = "lmbridge"
            version = project.version.toString()

            // Android AAR from release variant
            from(components.getByName("release"))

            // JVM JAR 추가
            artifact(tasks.named<Jar>("jvmJar"))
            artifact(jvmSourcesJar)

            pom {
                name.set("LMBridge")
                description.set("LLM Bridge Library for Android and JVM")
                url.set("https://github.com/isroot/lmbridge")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("isroot")
                        name.set("isroot")
                    }
                }
                scm {
                    url.set("https://github.com/isroot/lmbridge")
                    connection.set("scm:git:https://github.com/isroot/lmbridge.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
