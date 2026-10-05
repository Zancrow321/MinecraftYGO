# Builds edo9300/ygopro-core ("ocgcore") as a self-contained shared library for the mod.
# Mirrors the upstream premake5.lua (target "ocgcoreshared"):
#  - C++17, no RTTI, hidden visibility, OCGCORE_EXPORT_FUNCTIONS
#  - Lua 5.4 compiled as C++ with luaconf-customize.h force-included (required, see
#    OCG_DUEL_CREATION_INCOMPATIBLE_LUA_API), never a system Lua
#  - static C/C++ runtime so the library has no runtime dependencies
cmake_minimum_required(VERSION 3.20)
project(ocgcore_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
	set(CMAKE_BUILD_TYPE Release)
endif()
# Same output location for single- and multi-config generators (MSVC), so Gradle finds the artifacts.
foreach(cfg "" _DEBUG _RELEASE _RELWITHDEBINFO _MINSIZEREL)
	set(CMAKE_RUNTIME_OUTPUT_DIRECTORY${cfg} ${CMAKE_BINARY_DIR})
	set(CMAKE_LIBRARY_OUTPUT_DIRECTORY${cfg} ${CMAKE_BINARY_DIR})
endforeach()

set(CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ygopro-core)
if(NOT EXISTS ${CORE_DIR}/ocgapi.h OR NOT EXISTS ${CORE_DIR}/lua/src/lua.h)
	message(FATAL_ERROR "ygopro-core submodule missing, run: git submodule update --init --recursive")
endif()

# --- Lua (as C++) ---------------------------------------------------------------------------
# Like upstream's meson build, every Lua translation unit is wrapped in a generated .cpp file that
# includes luaconf-customize.h first. This compiles Lua as C++ on every compiler without relying on
# per-compiler "treat .c as C++" or force-include flags.
set(LUA_EXCLUDED lbitlib lcorolib ldblib linit loadlib loslib ltests lua luac lutf8lib onelua)
file(GLOB LUA_C_SOURCES ${CORE_DIR}/lua/src/*.c)
set(LUA_WRAPPED_SOURCES)
foreach(src ${LUA_C_SOURCES})
	get_filename_component(name ${src} NAME_WE)
	list(FIND LUA_EXCLUDED ${name} excluded)
	if(excluded EQUAL -1)
		set(wrapper ${CMAKE_CURRENT_BINARY_DIR}/lua_wrapped/${name}.cpp)
		file(WRITE ${wrapper}.tmp "#include \"${CORE_DIR}/lua/luaconf-customize.h\"\n#include \"${src}\"\n")
		configure_file(${wrapper}.tmp ${wrapper} COPYONLY)
		list(APPEND LUA_WRAPPED_SOURCES ${wrapper})
	endif()
endforeach()

add_library(ocg_lua STATIC ${LUA_WRAPPED_SOURCES})
target_include_directories(ocg_lua PUBLIC ${CORE_DIR}/lua/src)
if(MSVC)
	target_compile_options(ocg_lua PRIVATE /W0)
else()
	target_compile_options(ocg_lua PRIVATE -w)
endif()

# --- ocgcore shared library -------------------------------------------------------------------
file(GLOB CORE_SOURCES ${CORE_DIR}/*.cpp ${CORE_DIR}/RNG/*.cpp)
add_library(ocgcore SHARED ${CORE_SOURCES})
target_compile_definitions(ocgcore PRIVATE OCGCORE_EXPORT_FUNCTIONS $<$<CONFIG:Release>:NDEBUG>)
target_include_directories(ocgcore PRIVATE ${CORE_DIR})
target_link_libraries(ocgcore PRIVATE ocg_lua)
set_target_properties(ocgcore PROPERTIES OUTPUT_NAME ocgcore PREFIX "${CMAKE_SHARED_LIBRARY_PREFIX}")

if(MSVC)
	target_compile_definitions(ocgcore PRIVATE WIN32 _WIN32 NOMINMAX)
	target_compile_options(ocgcore PRIVATE /GR- /O2)
else()
	target_compile_options(ocgcore PRIVATE -fno-rtti -Wno-unused-parameter)
	if(WIN32)
		target_compile_definitions(ocgcore PRIVATE NOMINMAX UNICODE _UNICODE)
		target_link_options(ocgcore PRIVATE -static-libgcc -static-libstdc++ -static)
	elseif(APPLE)
		set_source_files_properties(${CORE_DIR}/processor_visit.cpp PROPERTIES COMPILE_OPTIONS -fno-exceptions)
	else()
		target_link_options(ocgcore PRIVATE -static-libgcc -static-libstdc++ -Wl,--no-undefined -Wl,--exclude-libs,ALL)
	endif()
endif()

# --- layout probe -----------------------------------------------------------------------------
# Prints sizeof/offsetof of the public structs as JSON; the Java FFM layouts are tested against it.
add_executable(ocg_layout_probe ${CMAKE_CURRENT_SOURCE_DIR}/probe/layout_probe.cpp)
target_include_directories(ocg_layout_probe PRIVATE ${CORE_DIR})

# --- constants dump ---------------------------------------------------------------------------
# Evaluates every #define of ocgapi_constants.h with the C++ compiler and prints NAME=value lines;
# :engine:generateOcgConstants turns them into OcgConstants.java.
file(STRINGS ${CORE_DIR}/ocgapi_constants.h CONSTANT_LINES REGEX "^#define[ \t]+[A-Z0-9_]+[ \t]+[^ \t]")
set(DUMP_SOURCE "#include <cstdio>\n#include \"ocgapi_types.h\"\n#include \"ocgapi_constants.h\"\nint main() {\n")
string(APPEND DUMP_SOURCE "\tstd::printf(\"OCG_VERSION_MAJOR=%llu\\n\", (unsigned long long)(OCG_VERSION_MAJOR));\n")
string(APPEND DUMP_SOURCE "\tstd::printf(\"OCG_VERSION_MINOR=%llu\\n\", (unsigned long long)(OCG_VERSION_MINOR));\n")
foreach(line ${CONSTANT_LINES})
	string(REGEX REPLACE "^#define[ \t]+([A-Z0-9_]+).*" "\\1" name "${line}")
	string(APPEND DUMP_SOURCE "\tstd::printf(\"${name}=%llu\\n\", (unsigned long long)(${name}));\n")
endforeach()
string(APPEND DUMP_SOURCE "\treturn 0;\n}\n")
file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/constants_dump.cpp.tmp "${DUMP_SOURCE}")
configure_file(${CMAKE_CURRENT_BINARY_DIR}/constants_dump.cpp.tmp ${CMAKE_CURRENT_BINARY_DIR}/constants_dump.cpp COPYONLY)
add_executable(ocg_constants_dump ${CMAKE_CURRENT_BINARY_DIR}/constants_dump.cpp)
target_include_directories(ocg_constants_dump PRIVATE ${CORE_DIR})
