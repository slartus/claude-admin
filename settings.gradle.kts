rootProject.name = "claude-admin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

include(":domain", ":data", ":presentation", ":app")
