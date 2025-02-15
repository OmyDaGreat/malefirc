import com.varabyte.kobweb.gradle.library.util.configAsKobwebLibrary

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kobweb.library)
}

group = "chat.auth"
version = "1.0-SNAPSHOT"

kotlin {
    configAsKobwebLibrary(includeServer = true)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":core"))
        }
        jsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.html.core)
            implementation(libs.kobweb.core)
            implementation(libs.kobweb.silk)
            implementation(libs.silk.icons.fa)
        }
        jvmMain.dependencies {
            implementation("org.jetbrains.exposed:exposed-core:0.41.1")
            implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
            implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
            implementation("org.postgresql:postgresql:42.3.1")
            compileOnly(libs.kobweb.api) // Provided by Kobweb backend at runtime
        }
    }
}
