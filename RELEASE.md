# Release process

Releases are automated with [JReleaser](https://jreleaser.org) on GitHub Actions. One manual **Release** run produces one release that publishes everything:

| Artifact | Destination |
| --- | --- |
| `dev.nthings:adf4j` (library jar + sources + javadoc) | [Maven Central](https://central.sonatype.com/artifact/dev.nthings/adf4j) |
| `adf4j-cli` native binaries — Linux, macOS, Windows | GitHub release assets |
| `adf4j-cli` WASM bundle (`.js` + `.wasm`) | GitHub release asset |
| `checksums_*.txt` + `.asc` signatures | GitHub release assets |

Between releases, every library change on `main` publishes a **snapshot** of `dev.nthings:adf4j` to the Central snapshots repository.

### Versioning model — the POM is the source of truth

The version lives in `pom.xml` as `X.Y.Z-SNAPSHOT` during development. A release (1) bumps the POM to `X.Y.Z` and commits it, (2) builds and publishes from that commit (JReleaser creates the `vX.Y.Z` tag + GitHub release), then (3) bumps the POM to the next `X.(Y+1).0-SNAPSHOT` and commits that. So the tagged commit carries the released version and the git history records every change. Versions follow [semver](https://semver.org).

The moving parts:

- [`jreleaser.yml`](jreleaser.yml) — what to sign, deploy, release, and attach.
- [`.github/workflows/release.yml`](.github/workflows/release.yml) — the release pipeline (manual).
- [`.github/workflows/snapshot.yml`](.github/workflows/snapshot.yml) — publishes library snapshots.
- [`pom.xml`](pom.xml) / [`adf4j-lib/pom.xml`](adf4j-lib/pom.xml) — the `publication` profile that produces the source/javadoc jars and a flattened, self-contained POM staged for Central.

---

## One-time setup

Done once before the first release. They need accounts and DNS access that CI cannot reach.

> [!IMPORTANT]
> **Make the repository public first.** On a private repo the GitHub Release assets aren't downloadable anonymously, and the POM `url`/`scm` + README links (`https://github.com/thiagogcm/adf4j`) return 404 to consumers and to Maven Central's validators. Publish before the first tag:
> ```bash
> gh repo edit thiagogcm/adf4j --visibility public --accept-visibility-change-consequences
> ```

### 1. Sonatype Central account + namespace

1. Create an account at <https://central.sonatype.com>.
2. Register the **`dev.nthings`** namespace (*Namespaces → Add Namespace*); Central issues a DNS `TXT` verification key.
3. Add that `TXT` record to the **`nthings.dev`** domain's DNS, then click *Verify*. The groupId `dev.nthings` maps to the domain `nthings.dev` — if you don't control it, change the `groupId` (in `pom.xml`, `adf4j-lib/pom.xml`, `jreleaser.yml`) to a namespace you can verify, e.g. `io.github.thiagogcm` (verified automatically via this GitHub account).
4. Generate a **user token** (*Account → Generate User Token*). The username/password pair becomes the `JRELEASER_MAVENCENTRAL_*` secrets below — not your login email/password.

### 2. GPG signing key

Maven Central requires every artifact to be GPG-signed and the public key discoverable on a keyserver.

```bash
gpg --full-generate-key                              # RSA 4096, real name/email
gpg --list-secret-keys --keyid-format=long           # find <KEY_ID> (hex after rsa4096/)
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>   # publish so Central can verify
gpg --armor --export             <KEY_ID> > gpg-public.asc
gpg --armor --export-secret-keys <KEY_ID> > gpg-secret.asc
```

### 3. GitHub repository secrets

Add these under *Settings → Secrets and variables → Actions* (the workflow maps them to the `JRELEASER_*` env vars JReleaser expects):

| Secret | Value |
| --- | --- |
| `JRELEASER_GPG_PUBLIC_KEY` | contents of `gpg-public.asc` (armored) |
| `JRELEASER_GPG_SECRET_KEY` | contents of `gpg-secret.asc` (armored) |
| `JRELEASER_GPG_PASSPHRASE` | the key's passphrase |
| `JRELEASER_MAVENCENTRAL_USERNAME` | Central **user token** username |
| `JRELEASER_MAVENCENTRAL_PASSWORD` | Central **user token** password |

```bash
gh secret set JRELEASER_GPG_PUBLIC_KEY  < gpg-public.asc
gh secret set JRELEASER_GPG_SECRET_KEY  < gpg-secret.asc
gh secret set JRELEASER_GPG_PASSPHRASE          # prompts
gh secret set JRELEASER_MAVENCENTRAL_USERNAME   # prompts
gh secret set JRELEASER_MAVENCENTRAL_PASSWORD   # prompts
rm -f gpg-public.asc gpg-secret.asc             # clean up exported keys
```

`GITHUB_TOKEN` is provided automatically (the jobs grant it `contents: write` to create the release and commit the version bumps); no secret needed.

> [!IMPORTANT]
> The release workflow commits the version bumps to `main` with `GITHUB_TOKEN`. If `main` is protected, allow `github-actions[bot]` to push (Branch protection → *Allow specified actors to bypass required pull requests*). A push made with `GITHUB_TOKEN` does **not** trigger other workflows, which is why the release is self-contained.

---

## Cutting a release

1. Make sure `main` is green (**Build and Test** passes) and the changelog-worthy commits are merged. Messages follow [Conventional Commits](https://www.conventionalcommits.org/) — JReleaser turns them into release notes.
2. *Actions → **Release** → Run workflow*, and fill in:
   - **releaseVersion** — the version to publish, e.g. `1.0.0` (or a pre-release like `1.0.0-rc.1`).
   - **nextVersion** *(optional)* — next development version, e.g. `1.1.0-SNAPSHOT`. Blank auto-bumps the minor (`1.0.0` → `1.1.0-SNAPSHOT`).
   - **dryRun** *(optional)* — see [Dry run](#dry-run).

The workflow then runs, in order: **prepare** (bump POM to `releaseVersion`, commit) → **build-native** (CLI on Linux/macOS/Windows runners — native-image can't be cross-compiled) → **build-wasm** → **release** (stage the library, then JReleaser `full-release`: sign, deploy to Central, tag, create the GitHub release, upload the CLI archives) → **finalize** (bump POM to `nextVersion`, commit).

### Pre-releases

A `releaseVersion` with a hyphen (`1.0.0-rc.1`) is tagged `v1.0.0-rc.1` and marked a **pre-release** on GitHub (via `prerelease.pattern` in `jreleaser.yml`). Central rejects `-SNAPSHOT` but accepts semver qualifiers like `-rc.1`.

> [!NOTE]
> For a pre-release, **always pass an explicit `nextVersion`** (e.g. cut `1.0.0-rc.1` with `nextVersion: 1.0.0-SNAPSHOT`). The blank auto-bump strips the qualifier and bumps the minor, skipping past the final (`1.0.0-rc.1` → `1.1.0-SNAPSHOT`).

---

## Publishing snapshots

The **Publish snapshot** workflow ([`snapshot.yml`](.github/workflows/snapshot.yml)) runs on every push to `main` that touches the library (and on demand). It stages the library at its `X.Y.Z-SNAPSHOT` version and runs `jreleaser deploy` to the Central snapshots repo, self-skipping if the POM isn't a `-SNAPSHOT`. Only the library is snapshotted; no GitHub release is created. It authenticates with the **same** Central user token, exposed to JReleaser's `nexus2` deployer as `JRELEASER_NEXUS2_SNAPSHOTS_USERNAME` / `_PASSWORD` (mapped from the `JRELEASER_MAVENCENTRAL_*` secrets).

Consume a snapshot by adding the Central snapshots repository:

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

---

## Dry run

Validate the whole pipeline without publishing or committing. *Actions → **Release** → Run workflow*, set **releaseVersion** and tick **dryRun**: this builds the native/WASM CLI, stages all artifacts, and runs `jreleaser full-release --dry-run` — no commits, tag, Central deploy, or release; **finalize** is skipped. Inspect the `jreleaser-logs` artifact afterward.

You can also validate locally:

```bash
# JReleaser config (version + token can be dummies for `config`)
JRELEASER_PROJECT_VERSION=1.0.0 JRELEASER_GITHUB_TOKEN=x \
JRELEASER_GPG_PUBLIC_KEY=x JRELEASER_GPG_SECRET_KEY=x JRELEASER_GPG_PASSPHRASE=x \
JRELEASER_MAVENCENTRAL_SONATYPE_USERNAME=x JRELEASER_MAVENCENTRAL_SONATYPE_PASSWORD=x \
jreleaser config

# Publication staging, end-to-end (no upload)
./mvnw -Ppublication -pl adf4j-lib -DskipTests clean deploy && find target/staging-deploy -type f
```

---

## Verifying a release

- **GitHub release** (<https://github.com/thiagogcm/adf4j/releases>): three native archives, the WASM zip, `checksums_*.txt`, and `.asc` signatures attached, with notes generated from the commits.
- **Maven Central**: deployments appear in the *Deployments* tab at <https://central.sonatype.com>. With `active: RELEASE` + `applyMavenCentralRules: true`, JReleaser uploads and releases automatically; artifacts are searchable at <https://central.sonatype.com/artifact/dev.nthings/adf4j> within ~10–30 minutes (and on `repo1.maven.org` later).

---

## Troubleshooting & rollback

- **Read the logs first.** The `release` job uploads `jreleaser-logs` (`trace.log`) on every run — it has the full JReleaser trace.
- **Central deploy rejected** (missing signature / javadoc / POM field): inspect the staged POM (`target/staging-deploy/.../adf4j-*.pom`) and the validation messages in `trace.log`. The flattened POM must carry `name`, `description`, `url`, `licenses`, `scm`, and `developers`.
- **Re-running a failed release**: JReleaser creates the tag with `overwrite: false`, so it won't clobber an existing release. Because **prepare** already committed the release-version bump, a clean re-run needs that commit undone. Typical recovery for a failure after the release commit but before the GitHub release:
  ```bash
  gh release delete vX.Y.Z --yes 2>/dev/null || true   # if it got created
  git push --delete origin vX.Y.Z 2>/dev/null || true  # if the tag got pushed
  git revert --no-edit <release-commit-sha>            # or reset main back to before it
  git push origin main
  ```
  then run the workflow again. A dry run first avoids most of this.
- **Maven Central is immutable**: a released version cannot be deleted or overwritten — fix a bad release by publishing a new patch version.
- **Snapshots** are mutable: re-publish freely by pushing again or re-running **Publish snapshot**.
