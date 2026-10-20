plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.6"
val lwjglNatives = "natives-linux"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
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

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // optIn.add("kotlin.RequiresOptIn")
    }
}


