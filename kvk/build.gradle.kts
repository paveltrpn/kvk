plugins {
    application
    kotlin("jvm")
}

val lwjglVersion = "3.3.6"
val lwjglNatives = "natives-linux"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
    implementation(project(":modules"))
    testImplementation(kotlin("test"))

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")

    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")

    implementation("org.lwjgl:lwjgl-vulkan")

    implementation("org.lwjgl:lwjgl-vma")
    implementation("org.lwjgl:lwjgl-vma:$lwjglVersion:$lwjglNatives")

    implementation("org.lwjgl:lwjgl-shaderc")
    implementation("org.lwjgl:lwjgl-shaderc:$lwjglVersion:$lwjglNatives")

    implementation("org.lwjgl:lwjgl-spvc")
    implementation("org.lwjgl:lwjgl-spvc:$lwjglVersion:$lwjglNatives")
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

// Run from the repo root so Config.workPath()/shaders resolves to
// <root>/shaders (JavaApplication has no workingDir in Gradle 8.13).
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
