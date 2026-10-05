#!/bin/bash
# SessionStart hook for Claude Code cloud sessions: JDK 25, submodules, warmed Gradle/native/upstream caches.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

JDK=/usr/lib/jvm/java-25-openjdk-amd64
if [ ! -x "$JDK/bin/javac" ]; then
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-25-jdk-headless cmake g++ >/dev/null
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=$JDK" >> "$CLAUDE_ENV_FILE"
fi
export JAVA_HOME=$JDK

git submodule update --init --recursive

# Warm caches: Gradle distribution + dependencies, host build of ocgcore, pinned card DB and scripts.
./gradlew --no-daemon -q :engine:testClasses :ocgcore-native:cmakeBuildHost :syncUpstreamCardScripts :syncUpstreamBabelCdb
