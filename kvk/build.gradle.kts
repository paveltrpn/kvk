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
