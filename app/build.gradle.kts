plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Plugin compilador para serialización
    //kotlin("plugin.serialization") version "1.9.23" // Usa la versión de Kotlin de tu proyecto

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    implementation(project(":utils"))
    implementation(libs.jsoup)
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.12")
    implementation("ch.qos.logback:logback-classic:1.6.3")

    // Runtime de kotlinx.serialization para JSON
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "org.fmmp.bibliascraper.AppKt"
}

//tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
//    exclude("**/Datas.kt", "**/Datas-v2.kt")
//}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    exclude("**/Datas.kt", "**/Datas-v2.kt")
//}

//sourceSets {
//    main {
//        kotlin {
//            exclude("**/Datas.kt, **/Datas-v2.kt")
//        }
//    }
//}

tasks.test {
    useJUnitPlatform()
}