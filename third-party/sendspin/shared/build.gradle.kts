plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}
kotlin {
    android {
        namespace = "com.sendspindroid.shared"
        compileSdk = 37
        minSdk = 31
        withHostTest {}
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvm()
    jvmToolchain(17)
    applyDefaultHierarchyTemplate()
    sourceSets {
        val jvmShared = create("jvmShared") {
            dependsOn(commonMain.get())
            dependencies { implementation("org.bouncycastle:bcprov-jdk18on:1.87") }
        }
        androidMain.get().dependsOn(jvmShared)
        jvmMain.get().dependsOn(jvmShared)
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            implementation("io.ktor:ktor-client-core:3.6.1")
            implementation("io.ktor:ktor-client-websockets:3.6.1")
            listOf("io.ktor:ktor-server-cio:3.6.1", "io.ktor:ktor-server-websockets:3.6.1").forEach {
                implementation(it) {
                    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
                    exclude(group = "org.fusesource.jansi", module = "jansi")
                }
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.6.1") }
        jvmMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.6.1") }
        jvmTest.dependencies { implementation(kotlin("test-junit")) }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation("io.mockk:mockk:1.14.12")
        }
    }
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}
