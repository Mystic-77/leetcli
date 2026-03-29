#!/usr/bin/env bash
# LeetCLI launcher for Linux / macOS
DIR="$(cd "$(dirname "$0")" && pwd)"
java -jar "$DIR/target/leetcli-1.0-SNAPSHOT.jar" "$@"
