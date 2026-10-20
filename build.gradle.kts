import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.4.0" apply false
}

val lwjglVersion = "3.3.6"
val lwjglNatives = "natives-linux"

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        repositories {
            mavenCentral()
        }

        dependencies {
            // String-invoke syntax — plain implementation(...) can't work in
            // the root subprojects block since those accessors are only generated for a module's own script.
            "testImplementation"(kotlin("test"))

            "implementation"(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

            "implementation"("org.lwjgl:lwjgl")
            "implementation"("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")

            "implementation"("org.lwjgl:lwjgl-glfw")
            "implementation"("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")

            "implementation"("org.lwjgl:lwjgl-vulkan")

            "implementation"("org.lwjgl:lwjgl-vma")
            "implementation"("org.lwjgl:lwjgl-vma:$lwjglVersion:$lwjglNatives")

            "implementation"("org.lwjgl:lwjgl-shaderc")
            "implementation"("org.lwjgl:lwjgl-shaderc:$lwjglVersion:$lwjglNatives")

            "implementation"("org.lwjgl:lwjgl-spvc")
            "implementation"("org.lwjgl:lwjgl-spvc:$lwjglVersion:$lwjglNatives")
        }

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }
}
