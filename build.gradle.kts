plugins {
    kotlin("jvm") version "1.8.20"
    application
}

group = "dev.knonm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")
    implementation("io.prometheus:simpleclient_pushgateway:0.16.0")
    implementation("io.ktor:ktor-server-core:2.2.4")
    implementation("io.ktor:ktor-server-cio:2.2.4")
    implementation("ch.qos.logback:logback-classic:1.4.6")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}