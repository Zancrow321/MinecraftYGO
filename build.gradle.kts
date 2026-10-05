import groovy.json.JsonSlurper

// Shared Java conventions for the plain Java modules (engine, tools). The NeoForge module configures itself.
subprojects {
    if (name == "neoforge") return@subprojects
    repositories {
        mavenCentral()
    }
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(25)
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release = 25
            options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing,-restricted", "-Werror"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgs("--enable-native-access=ALL-UNNAMED")
            testLogging {
                events("failed", "skipped")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStandardStreams = providers.gradleProperty("ygo.showTestOutput").isPresent
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pinned upstream checkouts (sources.lock.json) into build/upstream/<name>, shallow-fetched by commit.
// Used by engine tests (card DB + scripts) and by the :tools data pipeline.
// ---------------------------------------------------------------------------------------------
@Suppress("UNCHECKED_CAST")
val sourceLock = JsonSlurper().parse(file("sources.lock.json")) as Map<String, Any>
val upstreamRoot = layout.buildDirectory.dir("upstream")

fun registerUpstream(name: String) = tasks.register("syncUpstream" + name.replaceFirstChar(Char::uppercase)) {
    group = "upstream"
    description = "Shallow-fetches the pinned commit of '$name' into build/upstream/$name"
    @Suppress("UNCHECKED_CAST")
    val entry = sourceLock[name] as Map<String, String>
    val url = entry.getValue("url")
    val commit = entry.getValue("commit")
    val dir = upstreamRoot.map { it.dir(name).asFile }
    inputs.property("commit", commit)
    outputs.upToDateWhen { File(dir.get(), ".git/ygo-commit").takeIf(File::isFile)?.readText()?.trim() == commit }
    doLast {
        val target = dir.get()
        fun git(vararg args: String) {
            val process = ProcessBuilder(listOf("git", *args)).directory(target).inheritIO().start()
            check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed in $target" }
        }
        target.mkdirs()
        if (!File(target, ".git").isDirectory) {
            git("init", "--quiet")
            git("remote", "add", "origin", url)
        }
        git("fetch", "--quiet", "--depth", "1", "origin", commit)
        git("-c", "advice.detachedHead=false", "checkout", "--quiet", "--force", "FETCH_HEAD")
        File(target, ".git/ygo-commit").writeText(commit)
    }
}

val syncUpstreamCardScripts = registerUpstream("cardScripts")
val syncUpstreamBabelCdb = registerUpstream("babelCdb")
val syncUpstreamYgomcmodels = registerUpstream("ygomcmodels")
val syncUpstreamYgojson = registerUpstream("ygojson")

tasks.register("syncUpstreams") {
    group = "upstream"
    description = "Fetches all pinned upstream sources"
    dependsOn(syncUpstreamCardScripts, syncUpstreamBabelCdb, syncUpstreamYgomcmodels, syncUpstreamYgojson)
}
