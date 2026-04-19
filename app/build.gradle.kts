import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":presentation"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.decompose.core)
    implementation(libs.decompose.compose)

    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation(libs.kotlinx.coroutines.swing)

    implementation(libs.jediterm.ui)
    implementation(libs.jediterm.pty)
}

val packagingJdk: String? = run {
    val fromProp = project.findProperty("compose.javaHome") as? String
    if (!fromProp.isNullOrBlank()) return@run fromProp
    val fromEnv = System.getenv("COMPOSE_JDK")
    if (!fromEnv.isNullOrBlank()) return@run fromEnv
    val localProps = rootProject.file("local.properties")
    if (!localProps.isFile) return@run null
    val props = Properties().apply { localProps.inputStream().use { load(it) } }
    props.getProperty("compose.javaHome")?.takeIf { it.isNotBlank() }
}

compose.desktop {
    application {
        mainClass = "dev.claudeadmin.app.MainKt"
        if (packagingJdk != null) javaHome = packagingJdk
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "ClaudeAdmin"
            packageVersion = "1.0.0"
        }
        buildTypes.release.proguard {
            version.set("7.6.1")
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}
