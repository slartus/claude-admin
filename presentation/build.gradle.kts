plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":domain"))

    api(libs.decompose.core)
    api(libs.essenty.lifecycle.coroutines)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
