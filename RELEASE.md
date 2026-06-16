# Release guide

`adf4j` releases are automated with GitHub Actions and JReleaser.

One full release publishes:

| Artifact                                         | Destination           |
| ------------------------------------------------ | --------------------- |
| `dev.nthings:adf4j` jar, sources, javadocs, SBOM | Maven Central         |
| `adf4j-cli-<version>-linux-x86_64.tar.gz`        | GitHub release asset  |
| `adf4j-cli-<version>-osx-aarch64.tar.gz`         | GitHub release asset  |
| `adf4j-cli-<version>-windows-x86_64.zip`         | GitHub release asset  |
| `adf4j-wasm-<version>.zip`                       | GitHub release asset  |
| Checksums and `.asc` signatures                  | GitHub release assets |

Only the Java library is published to Maven Central. The native CLI and WASM bundle are distributed from GitHub Releases.

## Release model

`pom.xml` is the version source of truth.

- Development versions end in `-SNAPSHOT`, for example `1.1.0-SNAPSHOT`.
- Release versions do not end in `-SNAPSHOT`, for example `1.1.0` or `1.1.0-rc.1`.
- Tags are `v<version>`.
- Versions with a hyphen, such as `1.1.0-rc.1`, become GitHub pre-releases.

JReleaser creates the tag and GitHub release. The workflow commits the release-version bump before publishing and the next `-SNAPSHOT` bump after publishing.

Treat these as release-significant surfaces:

- exported Java API in `dev.nthings.adf4j`;
- ADF-to-Markdown conversion behavior, diagnostics, metadata, and unresolved references;
- CLI commands, options, exit codes, stdout/stderr contracts, and output formats;
- WASM bridge API, loader behavior, and packaged examples;
- Java/GraalVM/Binaryen runtime or build requirements.

## Required setup

Repository secrets:

| Secret                            | Purpose                             |
| --------------------------------- | ----------------------------------- |
| `JRELEASER_GPG_PUBLIC_KEY`        | Armored public signing key          |
| `JRELEASER_GPG_SECRET_KEY`        | Armored private signing key         |
| `JRELEASER_GPG_PASSPHRASE`        | Signing key passphrase              |
| `JRELEASER_MAVENCENTRAL_USERNAME` | Maven Central Portal token username |
| `JRELEASER_MAVENCENTRAL_PASSWORD` | Maven Central Portal token password |

The Release workflow pushes version commits with `GITHUB_TOKEN`, so branch protection must allow that path or the version commits need to be done manually.

Relevant files:

- [`pom.xml`](pom.xml) - version and reactor.
- [`adf4j-lib/pom.xml`](adf4j-lib/pom.xml) - Central `publication` staging.
- [`adf4j-cli/pom.xml`](adf4j-cli/pom.xml) - native CLI build.
- [`adf4j-wasm/pom.xml`](adf4j-wasm/pom.xml) - WASM build.
- [`jreleaser.yml`](jreleaser.yml) - signing, deploy, release, checksums, assets.
- [`.github/workflows/release.yml`](.github/workflows/release.yml) and [`.github/workflows/snapshot.yml`](.github/workflows/snapshot.yml) - release automation.

## Before release

Confirm:

- `main` is green.
- User-facing commits follow Conventional Commits for release notes.
- The current POM version is the expected `X.Y.Z-SNAPSHOT`.
- docs and examples match the released behavior.
- conversion, CLI, WASM, parser, URL, macro, and HTML-rendering changes have focused tests.

Useful local checks:

```bash
./mvnw -B -ntp verify
./mvnw -B -ntp -Ppublication -pl adf4j-lib -DskipTests clean deploy
find target/staging-deploy -type f | sort
```

Optional CLI/WASM checks:

```bash
./mvnw -B -ntp package -Pnative -pl adf4j-cli -am -DskipTests
./mvnw -B -ntp package -Pwasm -pl adf4j-wasm -am -DskipTests
node adf4j-wasm/src/test/js/test.mjs
```

## Cut release

Run **Actions** -> **Release** -> **Run workflow** on `main`.

Inputs:

- `releaseVersion`: version to publish, for example `1.1.0` or `1.1.0-rc.1`.
- `nextVersion`: next development version, for example `1.2.0-SNAPSHOT`.
- `dryRun`: build and validate without committing, tagging, publishing, or creating a release.

If `nextVersion` is blank, the workflow bumps the minor version:

```text
1.1.0 -> 1.2.0-SNAPSHOT
```

For pre-releases, always pass `nextVersion` explicitly. Otherwise `1.1.0-rc.1` auto-bumps to `1.2.0-SNAPSHOT`, not `1.1.0-SNAPSHOT`.

The workflow runs:

`Prepare` -> native CLI builds -> WASM build -> `jreleaser full-release` -> `Finalize`.

## Dry run

Use dry runs before changing release infrastructure or credentials.

A dry run builds native/WASM assets, stages the library, and runs:

```bash
jreleaser full-release --dry-run
```

It skips commits, tags, Central deployment, attestations, GitHub release creation, and the final version bump. Inspect the uploaded `jreleaser-logs` artifact.

## Verify release

Check:

- `main` has the release commit and the next snapshot commit.
- `https://github.com/thiagogcm/adf4j/releases/tag/v<version>` exists.
- release notes, native CLI archives, WASM zip, checksums, and signatures are present.
- `dev.nthings:adf4j:<version>` appears in Maven Central Portal.
- GitHub artifact attestations exist for jars and release archives.

Maven Central releases are immutable. Fix bad releases with a new patch version.

## Snapshots

The **Publish snapshot** workflow publishes only `dev.nthings:adf4j` snapshots. It runs on relevant pushes to `main` and manually, self-skips unless the version ends in `-SNAPSHOT`, stages with `-Ppublication`, and runs:

```bash
jreleaser deploy
```

Consumers can use:

```xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases><enabled>false</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
```

Snapshots are mutable; republish by pushing again or rerunning the workflow.

## Recovery

Start with the uploaded JReleaser logs: `trace.log` and `output.properties`.

- Central rejection: inspect `target/staging-deploy` and Central validation messages.
- Existing tag or release: `overwrite: false` means reruns will not replace `v<version>`.
- Final bump failure: manually set `nextVersion`, commit, and push `main`.
- Native or WASM failure: fix the platform build, clean partial release state, and rerun after a dry run.

Cleanup before rerunning a failed unpublished release:

```bash
gh release delete vX.Y.Z --yes 2>/dev/null || true
git push --delete origin vX.Y.Z 2>/dev/null || true
git revert --no-edit <release-commit-sha>
git push origin main
```
