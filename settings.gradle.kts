pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "MinecraftYGO"

include(":ocgcore-native", ":engine", ":tools")

// The NeoForge module needs maven.neoforged.net and the Mojang hosts; it can be skipped with -Pygo.skipMod=true.
val skipMod = providers.gradleProperty("ygo.skipMod").map(String::toBoolean).getOrElse(false)
if (!skipMod && file("neoforge/build.gradle.kts").isFile) {
    include(":neoforge")
}
