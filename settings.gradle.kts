// No arquivo settings.gradle.kts

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral() // <-- ESSENCIAL para o MediaPipe e ML Kit
    }
}
rootProject.name = "MyApplication3" // Verifique se o nome do seu projeto está correto
include(":app")