plugins {
    application
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
    implementation(project(":modules"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "kvk.MainKt"
}

// Run from the repo root so Config.workPath()/shaders resolves to
// <root>/shaders (JavaApplication has no workingDir in Gradle 8.13).
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
