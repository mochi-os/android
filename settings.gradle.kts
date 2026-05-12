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
        mavenCentral()
    }
}
rootProject.name = "Mochi"
include(":lib")
include(":app")
include(":apps:feeds")
include(":apps:chat")
include(":apps:forums")
include(":apps:projects")
