plugins {
    kotlin("jvm") version "2.1.20-Beta2"
    application
    java
}

group = "com.emrul"
version = "1.0-SNAPSHOT"


val graalvmVersion: String by project

repositories {
    mavenCentral()
}

dependencies {
    implementation("at.released.weh:bindings-chicory-wasip1:0.4")
    implementation("com.dylibso.chicory:runtime:1.1.1")

    implementation("at.released.weh:bindings-graalvm241-wasip1:0.3")
    implementation("at.released.weh:bindings-graalvm241-emscripten:0.3")
    implementation("org.graalvm.polyglot:polyglot:${graalvmVersion}")
    implementation("org.graalvm.polyglot:wasm:${graalvmVersion}")
    implementation("org.graalvm.polyglot:tools:${graalvmVersion}")

    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23)) // Use your Java version
    }
    sourceCompatibility = JavaVersion.VERSION_23
    targetCompatibility = JavaVersion.VERSION_23
}

kotlin {
    jvmToolchain(23)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23
        javaParameters = true
    }
}


buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin", version = "2.0.10"))
    }
}