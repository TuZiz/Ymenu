#!/usr/bin/env sh
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$APP_HOME/.gradle/wrapper"
mkdir -p "$WRAPPER_DIR"
if [ ! -f "$WRAPPER_DIR/gradle-wrapper.jar" ]; then
  curl -L -o "$WRAPPER_DIR/gradle-wrapper.jar" https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar
fi
if [ ! -f "$WRAPPER_DIR/gradle-wrapper.properties" ]; then
  cat > "$WRAPPER_DIR/gradle-wrapper.properties" <<'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
fi
exec java -classpath "$WRAPPER_DIR/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
