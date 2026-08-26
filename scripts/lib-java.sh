#!/usr/bin/env bash
# shellcheck shell=bash

# Shared JDK selection for the dev scripts.
#
# The Maven build requires a JDK >= 21 (maven.compiler.release in the parent
# POM); Apache Hop GUI/run are started with the same JDK. This helper picks a
# suitable JDK with the following priority:
#
#   1. $HOP_JAVA_HOME, when set and >= 21 (existing configuration wins);
#   2. $JAVA_HOME, when set and >= 21;
#   3. SDKMAN candidates below $HOME/.sdkman/candidates/java: every entry with
#      a runnable bin/java whose major version is >= 21; unsuitable candidates
#      (e.g. Java 8, 11 or 17) are ignored, as are symlinks (SDKMAN's
#      "current");
#   4. among the valid candidates Temurin ("*-tem") is preferred, then the
#      highest version (major, minor, patch compared numerically).
#
# The result is stored in SELECTED_JAVA_HOME. On failure an actionable message
# is printed and the function returns 1; the scripts abort under "set -e".

# Minimum Java major version required by the build.
INTERLIS_MIN_JAVA_MAJOR=21

# Extracts the major version of a JDK by asking its java binary (the directory
# names of SDKMAN candidates are not reliable, e.g. the "-nik" entries carry
# the glibc version in their suffix). Prints 0 when the version is unknown.
java_major_version() {
  local java_bin="$1"
  local version
  version="$("$java_bin" -version 2>&1 | head -n 1)"
  if [[ "$version" =~ \"1\.([0-9]+) ]]; then
    # legacy "1.8.0_412" style
    echo "${BASH_REMATCH[1]}"
  elif [[ "$version" =~ \"([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
  else
    echo "0"
  fi
}

# Prints "major.minor.patch" zero-padded so two tuples compare lexically.
# Unknown parts become 00. Uses "read" instead of "cut": BSD cut (macOS)
# returns the whole line for a missing field, which would corrupt the tuple.
java_version_tuple() {
  local java_bin="$1"
  local version major minor patch
  version="$("$java_bin" -version 2>&1 | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')"
  if [[ "$version" == 1.* ]]; then
    # legacy "1.8.0_412" style: cut the build suffix before splitting
    version="${version%%_*}"
    IFS=. read -r _ major minor patch <<< "$version"
  else
    IFS=. read -r major minor patch <<< "$version"
  fi
  patch="${patch%%[-_+]*}"
  major="${major//[^0-9]/}"
  minor="${minor//[^0-9]/}"
  patch="${patch//[^0-9]/}"
  major="${major:-0}"
  minor="${minor:-0}"
  patch="${patch:-0}"
  printf '%02d.%02d.%02d' "$((10#$major))" "$((10#$minor))" "$((10#$patch))"
}

# Returns the JDK home with the higher version (zero-padded tuple comparison).
java_newer_home() {
  local current="$1" candidate="$2"
  [[ -n "$current" ]] || {
    echo "$candidate"
    return 0
  }
  local current_tuple candidate_tuple
  current_tuple="$(java_version_tuple "$current/bin/java")"
  candidate_tuple="$(java_version_tuple "$candidate/bin/java")"
  if [[ "$candidate_tuple" > "$current_tuple" ]]; then
    echo "$candidate"
  else
    echo "$current"
  fi
}

select_java_home() {
  SELECTED_JAVA_HOME=""

  if [[ -n "${HOP_JAVA_HOME:-}" ]]; then
    if [[ -x "$HOP_JAVA_HOME/bin/java" ]]; then
      local major
      major="$(java_major_version "$HOP_JAVA_HOME/bin/java")"
      if ((major >= INTERLIS_MIN_JAVA_MAJOR)); then
        SELECTED_JAVA_HOME="$HOP_JAVA_HOME"
        return 0
      fi
      echo "Ignoring HOP_JAVA_HOME=$HOP_JAVA_HOME: Java $major is older than the required $INTERLIS_MIN_JAVA_MAJOR" >&2
    else
      echo "Ignoring HOP_JAVA_HOME=$HOP_JAVA_HOME: $HOP_JAVA_HOME/bin/java is not executable" >&2
    fi
  fi

  if [[ -n "${JAVA_HOME:-}" ]]; then
    if [[ -x "$JAVA_HOME/bin/java" ]]; then
      local major
      major="$(java_major_version "$JAVA_HOME/bin/java")"
      if ((major >= INTERLIS_MIN_JAVA_MAJOR)); then
        SELECTED_JAVA_HOME="$JAVA_HOME"
        return 0
      fi
      echo "Ignoring JAVA_HOME=$JAVA_HOME: Java $major is older than the required $INTERLIS_MIN_JAVA_MAJOR" >&2
    else
      echo "Ignoring JAVA_HOME=$JAVA_HOME: $JAVA_HOME/bin/java is not executable" >&2
    fi
  fi

  local sdkman_dir="${HOP_SDKMAN_JAVA_DIR:-$HOME/.sdkman/candidates/java}"
  if [[ ! -d "$sdkman_dir" ]]; then
    echo "No suitable JDK found: HOP_JAVA_HOME/JAVA_HOME are not usable and the SDKMAN candidate directory $sdkman_dir does not exist" >&2
    echo "Install a JDK >= $INTERLIS_MIN_JAVA_MAJOR, e.g.: sdk install java ${INTERLIS_MIN_JAVA_MAJOR}-tem" >&2
    return 1
  fi

  local entry candidate major best_tem="" best_any=""
  for entry in "$sdkman_dir"/*; do
    # skip symlinks (SDKMAN "current") and non-directories
    [[ -L "$entry" ]] && continue
    [[ -d "$entry" ]] || continue
    [[ -x "$entry/bin/java" ]] || continue
    major="$(java_major_version "$entry/bin/java")"
    ((major >= INTERLIS_MIN_JAVA_MAJOR)) || continue
    candidate="$(basename "$entry")"
    if [[ "$candidate" == *-tem* ]]; then
      best_tem="$(java_newer_home "$best_tem" "$entry")"
    fi
    best_any="$(java_newer_home "$best_any" "$entry")"
  done

  if [[ -n "$best_tem" ]]; then
    SELECTED_JAVA_HOME="$best_tem"
    return 0
  fi
  if [[ -n "$best_any" ]]; then
    SELECTED_JAVA_HOME="$best_any"
    return 0
  fi

  echo "No suitable JDK found in $sdkman_dir: every candidate is older than Java $INTERLIS_MIN_JAVA_MAJOR or broken" >&2
  echo "Install a JDK >= $INTERLIS_MIN_JAVA_MAJOR, e.g.: sdk install java ${INTERLIS_MIN_JAVA_MAJOR}-tem" >&2
  return 1
}
