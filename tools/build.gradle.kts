// Build-time data pipeline: card pool, engine data, card texts, booster sets, script bundle and the
// bbmodel -> GeckoLib conversion of YGOMCModels. Not part of the mod jar; outputs are committed.
plugins {
    java
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.gson)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val upstream = rootProject.layout.buildDirectory.dir("upstream").get().asFile
val engineGenerated = rootProject.file("engine/src/generated/resources/minecraftygo/data")
val modGenerated = rootProject.file("neoforge/src/generated/resources")
val dataDir = file("data")
val reportFile = rootProject.file("docs/PoolReport.md")

val generatePool = tasks.register<JavaExec>("generatePool") {
    group = "data"
    description = "Maps YGOMCModels to cards, computes the era card pool and writes engine data, texts and sets"
    dependsOn(":syncUpstreamYgojson", ":syncUpstreamBabelCdb", ":syncUpstreamCardScripts", ":syncUpstreamYgomcmodels")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.zancrow321.minecraftygo.tools.pool.GeneratePool"
    args(upstream, dataDir, engineGenerated, reportFile)
    maxHeapSize = "3g"
}

val convertModels = tasks.register<JavaExec>("convertModels") {
    group = "data"
    description = "Converts the YGOMCModels bbmodels of the card pool to GeckoLib geometry, animations and textures"
    dependsOn(":syncUpstreamYgomcmodels")
    mustRunAfter(generatePool)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.zancrow321.minecraftygo.tools.bbmodel.ConvertModels"
    args(upstream.resolve("ygomcmodels"), dataDir, engineGenerated.resolve("pool.json"), modGenerated)
    maxHeapSize = "2g"
}

val processAssets = tasks.register<JavaExec>("processAssets") {
    group = "data"
    description = "Validates/converts the Blockbench assets in assets-src (placeholders for missing ones) and arena layouts"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.zancrow321.minecraftygo.tools.assets.ProcessAssets"
    args(rootProject.projectDir)
}

tasks.register<JavaExec>("validateAssets") {
    group = "verification"
    description = "Checks assets-src/blockbench against assets-src/asset-contract.json without writing anything"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.zancrow321.minecraftygo.tools.assets.ProcessAssets"
    args(rootProject.projectDir, "--check")
}

tasks.register("regenerate") {
    group = "data"
    description = "Runs the complete data pipeline"
    dependsOn(generatePool, convertModels, processAssets)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("ygo.rootDir", rootProject.projectDir.absolutePath)
}
