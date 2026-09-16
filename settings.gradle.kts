pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.testing\\.platform.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy { eachPlugin {
        if(requested.id.id=="com.android.application") useModule("com.android.tools.build:gradle:${requested.version}")
        if(requested.id.id in listOf("org.jetbrains.kotlin.android","org.jetbrains.kotlin.kapt")) useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
        if(requested.id.id=="org.jetbrains.kotlin.plugin.compose") useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
    } }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.testing\\.platform.*") } }
        mavenCentral()
    }
}
rootProject.name="DoNote"
include(":app")
