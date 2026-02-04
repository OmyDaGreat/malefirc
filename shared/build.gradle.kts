plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kobweb.library)
}

group = "xyz.malefic.irc.shared"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()
    js(IR) {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        jsMain.dependencies {
        }
        jvmMain.dependencies {
        }
    }
}
