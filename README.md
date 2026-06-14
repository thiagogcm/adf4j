# adf4j

Atlassian Document Format (ADF) processing for Java. Convert ADF JSON to Markdown, extract references/attachments/outline, and validate documents — from a Java library or a standalone CLI.

- **Library** (`dev.nthings:adf4j`) — published to Maven Central.
- **CLI** — distributed as GraalVM native executables (Linux, macOS, Windows) and a WASM build, attached to each [GitHub release](https://github.com/thiagogcm/adf4j/releases).

## Library

```xml
<dependency>
    <groupId>dev.nthings</groupId>
    <artifactId>adf4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

Requires Java 25+. See [docs/usage-guide.md](docs/usage-guide.md) and
[docs/architecture.md](docs/architecture.md).

## CLI

Download the archive for your platform from the [latest release](https://github.com/thiagogcm/adf4j/releases/latest), extract it, and run the `adf4j-cli` binary:

```
adf4j - Atlassian Document Format (ADF) processing
Usage: adf4j <command> [options] [<input-file>]

Commands:
  convert    Convert ADF JSON to Markdown
  analyze    Extract references, attachments, and outline as JSON
  validate   Parse-check ADF JSON and report diagnostics
```

Input is read from `<input-file>` or stdin. Run `adf4j <command> --help` for the full options.

```bash
adf4j convert doc.adf.json
cat doc.adf.json | adf4j convert -t "My Page" -o out.md
adf4j analyze --select referencedFileIds,outline doc.adf.json
adf4j validate --fail-on-warning doc.adf.json
```

The WASM build (`adf4j-cli-<version>-wasm.zip`) ships `adf4j-cli.js` + `adf4j-cli.js.wasm` and runs on a JavaScript host (e.g. Node).

## Building from source

```bash
./mvnw verify                                  # build + test + coverage gate
./mvnw package -Pnative -pl adf4j-cli -am -DskipTests   # native CLI for the current OS
./mvnw package -Pwasm   -pl adf4j-cli -am -DskipTests   # WASM CLI
```

## Releasing

Releases are automated with JReleaser and GitHub Actions. See [RELEASE.md](RELEASE.md).

## License

[Apache-2.0](LICENSE).
