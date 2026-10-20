plugins {
    application
    kotlin("jvm") version "2.4.0"
}

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.6"
val lwjglNatives = "natives-linux"

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl", classifier = lwjglNatives)

    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)

    implementation("org.lwjgl", "lwjgl-vulkan")

    implementation("org.lwjgl", "lwjgl-vma")
    implementation("org.lwjgl", "lwjgl-vma", classifier = lwjglNatives)

    implementation("org.lwjgl", "lwjgl-shaderc")
    implementation("org.lwjgl", "lwjgl-shaderc", classifier = lwjglNatives)

    implementation("org.lwjgl", "lwjgl-spvc")
    implementation("org.lwjgl", "lwjgl-spvc", classifier = lwjglNatives)

    implementation(project(":modules"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // optIn.add("kotlin.RequiresOptIn")
    }
}

application {
    mainClass = "kvk.MainKt"
}

