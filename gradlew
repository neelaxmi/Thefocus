#!/bin/sh
# Minimal Gradle wrapper launcher. Requires Java and gradle/wrapper/gradle-wrapper.jar.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi
if [ ! -x "$JAVACMD" ] && ! command -v "$JAVACMD" >/dev/null 2>&1; then
  echo "ERROR: Java not found. Install JDK 17 and set JAVA_HOME." >&2
  exit 1
fi
exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
