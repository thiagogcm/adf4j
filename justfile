# adf4j task runner - the single source of truth for build, test, and release commands.
#
# Local devs and CI run the SAME recipes: the GitHub Actions workflows install `just` and call
# these recipes instead of inlining Maven/npm/node invocations. Keep CI-only orchestration
# (git identity, secrets, attestation, artifact transfer, platform archive naming, JReleaser)
# in the workflows; keep the build/test/release commands here. See RELEASE.md for the release model.
#
# Quick start:  just            (list recipes)
#               just verify     (the canonical build + test gate)
#               just release 1.0.2 1.1.0-SNAPSHOT   (trigger a GitHub release)

# Fail fast, treat unset vars as errors, propagate pipe failures. Same shell on Windows (Git bash)
# so `./mvnw` and the node scripts behave identically on every runner.
set shell := ["bash", "-euo", "pipefail", "-c"]
set windows-shell := ["bash", "-euo", "pipefail", "-c"]

# Batch mode, no transfer progress: quiet and CI-friendly, and identical for local runs.
mvn := "./mvnw -B -ntp"

# List available recipes.
default:
    @just --list

# --- Build --------------------------------------------------------------------

# Compile and package the library and CLI jars (no native image, skip tests).
[group('build')]
build:
    {{mvn}} -DskipTests clean package

# Compile the library and CLI without tests or packaging (used by CodeQL SAST).
[group('build')]
compile:
    {{mvn}} -DskipTests clean compile

# Build the GraalVM native CLI executable (requires GraalVM native-image).
[group('build')]
native:
    {{mvn}} package -Pnative -pl adf4j-cli -am -DskipTests

# Build the GraalVM WASM web image (requires GraalVM native-image + Binaryen wasm-as >= 119).
[group('build')]
wasm:
    {{mvn}} package -Pwasm -pl adf4j-wasm -am -DskipTests

# Remove Maven, dist, and JReleaser build outputs.
[group('build')]
clean:
    {{mvn}} clean
    rm -rf dist out

# --- Test ---------------------------------------------------------------------

# Run unit tests only.
[group('test')]
test:
    {{mvn}} test

# Canonical gate: compile, test, coverage check (jacoco), and Spotless check.
[group('test')]
verify:
    {{mvn}} clean verify

# Load the built WASM image from Node and assert it converts. Run `just wasm` first.
[group('test')]
wasm-smoke:
    node adf4j-wasm/src/test/js/test.mjs

# --- Quality ------------------------------------------------------------------

# Apply Spotless formatting in place.
[group('quality')]
format:
    {{mvn}} spotless:apply

# Verify Spotless formatting without modifying files.
[group('quality')]
format-check:
    {{mvn}} spotless:check

# --- Package ------------------------------------------------------------------

# Assemble + verify the npm package in dist/npm (current Maven version if omitted; run `just wasm` first).
[group('package')]
wasm-npm version="":
    #!/usr/bin/env bash
    set -euo pipefail
    version="{{version}}"
    if [[ -z "$version" ]]; then
        version="$({{mvn}} -q -pl adf4j-lib help:evaluate -Dexpression=project.version -DforceStdout)"
    fi
    node adf4j-wasm/scripts/prepare-npm-package.mjs "$version" dist/npm/adf4j-wasm
    node adf4j-wasm/scripts/verify-npm-package.mjs dist/npm/adf4j-wasm

# --- Release ------------------------------------------------------------------

# Print the current Maven project version (consumed by CI and the recipes above).
[group('release')]
version:
    @{{mvn}} -q -pl adf4j-lib help:evaluate -Dexpression=project.version -DforceStdout

# Set the Maven (all modules) and npm package versions in place. Does not commit.
[group('release')]
set-version version:
    {{mvn}} versions:set -DnewVersion={{version}} -DprocessAllModules -DgenerateBackupPoms=false
    npm version {{version}} --prefix adf4j-wasm/src/npm --no-git-tag-version --allow-same-version

# Stage the library to target/staging-deploy for Maven Central (JReleaser uploads it).
[group('release')]
stage:
    {{mvn}} -Ppublication -pl adf4j-lib -DskipTests clean deploy

# Trigger the GitHub Release workflow (publishes to Maven Central + npm; blank next = minor bump; `--yes` skips the prompt).
[confirm("Trigger the Release workflow? This publishes to Maven Central (immutable) and npm.")]
[group('release')]
release version next="" dry="false":
    #!/usr/bin/env bash
    set -euo pipefail
    args=(-f "releaseVersion={{version}}" -f "dryRun={{dry}}")
    [[ -n "{{next}}" ]] && args+=(-f "nextVersion={{next}}")
    gh workflow run release.yml "${args[@]}"
    echo "Release workflow dispatched for {{version}} (dryRun={{dry}}). Watch it with: just release-watch"

# Watch the most recent Release workflow run.
[group('release')]
release-watch:
    gh run watch "$(gh run list --workflow=release.yml --limit 1 --json databaseId --jq '.[0].databaseId')"

# Report the versions of the tools the build and release path need.
[group('release')]
doctor:
    #!/usr/bin/env bash
    set -uo pipefail
    echo "java:  $(java -version 2>&1 | head -1)"
    echo "node:  $(node --version)"
    echo "mvnw:  $({{mvn}} -v 2>/dev/null | head -1)"
    command -v gh   >/dev/null && echo "gh:    $(gh --version | head -1)"   || echo "gh:    MISSING (needed for 'just release')"
    command -v just >/dev/null && echo "just:  $(just --version)"           || echo "just:  MISSING"
