// Builds the ocgcore shared library for the host platform with CMake (see CMakeLists.txt).
// Other platforms are built by .github/workflows/natives.yml and dropped into prebuilt/<platform>/.

val cmakeDir = layout.buildDirectory.dir("cmake")
val hostLibraryName = when {
    org.gradle.internal.os.OperatingSystem.current().isWindows -> "ocgcore.dll"
    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "libocgcore.dylib"
    else -> "libocgcore.so"
}
val exeSuffix = if (org.gradle.internal.os.OperatingSystem.current().isWindows) ".exe" else ""
val probeName = "ocg_layout_probe$exeSuffix"
val constantsDumpName = "ocg_constants_dump$exeSuffix"

val cmakeConfigureHost = tasks.register<Exec>("cmakeConfigureHost") {
    group = "native"
    description = "Configures the host CMake build of ocgcore"
    inputs.file("CMakeLists.txt")
    outputs.file(cmakeDir.map { it.file("CMakeCache.txt") })
    commandLine("cmake", "-S", projectDir.absolutePath, "-B", cmakeDir.get().asFile.absolutePath, "-DCMAKE_BUILD_TYPE=Release")
}

tasks.register<Exec>("cmakeBuildHost") {
    group = "native"
    description = "Builds ocgcore and the layout probe for the host platform"
    dependsOn(cmakeConfigureHost)
    inputs.files(fileTree("ygopro-core") { include("**/*.cpp", "**/*.h", "**/*.hpp", "lua/src/*.c", "lua/*.h") })
    inputs.files("CMakeLists.txt", "probe/layout_probe.cpp")
    outputs.files(
        cmakeDir.map { it.file(hostLibraryName) },
        cmakeDir.map { it.file(probeName) },
        cmakeDir.map { it.file(constantsDumpName) },
    )
    commandLine(
        "cmake", "--build", cmakeDir.get().asFile.absolutePath, "--config", "Release",
        "--parallel", Runtime.getRuntime().availableProcessors().toString(),
    )
}

/** Absolute path of the host library produced by [cmakeBuildHost] (consumed by engine tests). */
extra["hostLibrary"] = cmakeDir.map { it.file(hostLibraryName).asFile.absolutePath }
extra["layoutProbe"] = cmakeDir.map { it.file(probeName).asFile.absolutePath }
extra["constantsDump"] = cmakeDir.map { it.file(constantsDumpName).asFile.absolutePath }
