#!/usr/bin/env bash
set -euo pipefail

# run-tests.sh - compile and run JUnit tests for the ScroogeCoin project
# Usage:
#   ./run-tests.sh            # compile and run default test IsValidTest
#   ./run-tests.sh IsValidTest  # run specific test class (must be compiled)
#   ./run-tests.sh clean      # remove bin/ directory

ROOT="$(cd "$(dirname "$0")" && pwd)"
LIB="$ROOT/lib"
BIN="$ROOT/bin"
JUNIT_JAR="$LIB/junit-4.13.2.jar"
HAMCREST_JAR="$LIB/hamcrest-core-1.3.jar"

TEST_CLASS="IsValidTest"
if [ "$#" -gt 0 ]; then
  if [ "$1" = "clean" ]; then
    echo "Cleaning $BIN"
    rm -rf "$BIN"
    exit 0
  fi
  TEST_CLASS="$1"
fi

mkdir -p "$LIB" "$BIN"

# Download jars if missing
if [ ! -f "$JUNIT_JAR" ]; then
  echo "Downloading junit-4.13.2.jar to $JUNIT_JAR..."
  wget -q -O "$JUNIT_JAR" "https://search.maven.org/remotecontent?filepath=junit/junit/4.13.2/junit-4.13.2.jar"
fi
if [ ! -f "$HAMCREST_JAR" ]; then
  echo "Downloading hamcrest-core-1.3.jar to $HAMCREST_JAR..."
  wget -q -O "$HAMCREST_JAR" "https://search.maven.org/remotecontent?filepath=org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
fi

echo "Compiling project into $BIN..."
# compile sources and tests; if package declarations exist this assumes top-level default package
javac -cp "$JUNIT_JAR:$HAMCREST_JAR:." -d "$BIN" *.java test/*.java

echo "Running JUnit tests (class: $TEST_CLASS)..."
java -cp "$BIN:$JUNIT_JAR:$HAMCREST_JAR" org.junit.runner.JUnitCore "$TEST_CLASS"

EXIT_CODE=$?
if [ $EXIT_CODE -eq 0 ]; then
  echo "\nTests completed: SUCCESS"
else
  echo "\nTests completed: FAIL (exit code $EXIT_CODE)"
fi
exit $EXIT_CODE
