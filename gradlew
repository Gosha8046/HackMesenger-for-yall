#!/bin/sh
#
# Gradle wrapper script
#

# Attempt to set APP_HOME
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"

exec "${JAVACMD:-java}" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
