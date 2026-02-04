plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    application
}

group = "xyz.malefic.irc.server"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("xyz.malefic.irc.server.MainKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.network)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.exposed)
    implementation(libs.postgres)
    implementation(libs.logback.classic)
    implementation(libs.kobweb.api)
}
