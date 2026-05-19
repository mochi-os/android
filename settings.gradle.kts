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
        // chesslib (com.github.bhlangonijr:chesslib) is published via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Mochi"
include(":lib")
include(":app")
include(":apps:feeds")
include(":apps:chat")
include(":apps:forums")
include(":apps:projects")
include(":apps:crm")
include(":apps:people")
include(":apps:settings")
include(":apps:wikis")
include(":apps:chess")
include(":apps:go")
include(":apps:words")
include(":apps:market")
include(":apps:staff")
