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

rootProject.name = "AutoPanelApp"

include(":app")
include(":core:model")
include(":core:data")
include(":core:domain")
include(":core:ui")
include(":feature:login")
include(":feature:task")
include(":feature:env")
include(":feature:script")
include(":feature:dependency")
include(":feature:log")
include(":feature:settings")
include(":feature:backup")
