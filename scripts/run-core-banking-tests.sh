#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  SELECTED_JAVA_HOME="${JAVA_HOME}"
else
  HOMEBREW_OPENJDK_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  if [[ -x "${HOMEBREW_OPENJDK_HOME}/bin/java" ]]; then
    SELECTED_JAVA_HOME="${HOMEBREW_OPENJDK_HOME}"
  elif command -v brew >/dev/null 2>&1; then
    BREW_PREFIX="$(brew --prefix openjdk@21 2>/dev/null || true)"
    if [[ -n "${BREW_PREFIX}" && -x "${BREW_PREFIX}/libexec/openjdk.jdk/Contents/Home/bin/java" ]]; then
      SELECTED_JAVA_HOME="${BREW_PREFIX}/libexec/openjdk.jdk/Contents/Home"
    fi
  fi
fi

if [[ -z "${SELECTED_JAVA_HOME:-}" ]]; then
  echo "OpenJDK 21 was not found. Install it with: brew install openjdk@21" >&2
  exit 1
fi

export JAVA_HOME="${SELECTED_JAVA_HOME}"
export PATH="${JAVA_HOME}/bin:${PATH}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-${ROOT_DIR}/.gradle}"

GRADLE_ARGS=("$@")
HAS_EXPLICIT_TASK="false"
for ARG in "${GRADLE_ARGS[@]}"; do
  if [[ "${ARG}" == :* ]]; then
    HAS_EXPLICIT_TASK="true"
    break
  fi
done

if [[ "$#" -eq 0 || "${HAS_EXPLICIT_TASK}" == "false" ]]; then
  GRADLE_ARGS=(:services:core-banking:test :services:core-banking:integrationTest "${GRADLE_ARGS[@]}")
fi

echo "JAVA_HOME=${JAVA_HOME}"
java -version
echo "GRADLE_USER_HOME=${GRADLE_USER_HOME}"

exec "${ROOT_DIR}/gradlew" "${GRADLE_ARGS[@]}" --no-daemon
