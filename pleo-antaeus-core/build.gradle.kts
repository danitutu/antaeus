plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    implementation("io.arrow-kt:arrow-core:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    api(project(":pleo-antaeus-models"))
    testImplementation("io.kotest:kotest-runner-junit5:5.3.0")
}