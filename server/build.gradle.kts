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
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kermit)
    implementation(libs.kobweb.api)
}
