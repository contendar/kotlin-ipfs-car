pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kotlin-ipfs-car"
include(":library",":sample-compose-app")
if (System.getenv("JITPACK") == null) {
    include(":sample-app")
}
