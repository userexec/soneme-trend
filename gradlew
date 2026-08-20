#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Prefer a standard Gradle wrapper JAR if one is present.
if [ -f "$WRAPPER_JAR" ]; then
  exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

# Bootstrap fallback for source bundles that intentionally omit the binary wrapper JAR.
URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | sed 's/\\:/:/g')
EXPECTED_SHA=$(sed -n 's/^distributionSha256Sum=//p' "$PROPS")
ZIP_NAME=${URL##*/}
DIST_NAME=${ZIP_NAME%-bin.zip}
DIST_NAME=${DIST_NAME%-all.zip}
CACHE_BASE=${GRADLE_USER_HOME:-"$HOME/.gradle"}/soneme-wrapper
DIST_DIR="$CACHE_BASE/$DIST_NAME"
ZIP="$CACHE_BASE/$ZIP_NAME"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  mkdir -p "$CACHE_BASE"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$URL"
    else
      echo "Gradle bootstrap needs curl or wget." >&2
      exit 1
    fi
  fi
  if [ -n "$EXPECTED_SHA" ]; then
    command -v sha256sum >/dev/null 2>&1 || { echo "Gradle bootstrap needs sha256sum." >&2; exit 1; }
    ACTUAL_SHA=$(sha256sum "$ZIP" | awk '{print $1}')
    if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
      echo "Gradle distribution checksum mismatch." >&2
      rm -f "$ZIP"
      exit 1
    fi
  fi
  command -v unzip >/dev/null 2>&1 || { echo "Gradle bootstrap needs unzip." >&2; exit 1; }
  rm -rf "$DIST_DIR"
  unzip -q "$ZIP" -d "$CACHE_BASE"
fi

exec "$DIST_DIR/bin/gradle" -p "$APP_HOME" "$@"
