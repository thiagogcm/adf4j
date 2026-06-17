# adf4j

Java tools for Atlassian Document Format (ADF). adf4j converts Confluence and Jira rich-text JSON to GitHub-Flavored Markdown, extracts document metadata, and validates ADF structure.

- **Library:** `dev.nthings:adf4j`, published to Maven Central.
- **CLI:** native executables for Linux, macOS, and Windows, attached to each [GitHub release](https://github.com/thiagogcm/adf4j/releases).
- **WASM:** `@nthings.dev/adf4j-wasm`, published to npm and attached to each GitHub release.

adf4j is immutable, thread-safe, dependency-light, and I/O-free. It never calls Confluence, a CDN, or a database. Page, media, attachment, and macro lookups stay in your application through resolver callbacks.

> [!WARNING]
> This project was built with assistance from AI agents. While the output was reviewed and validated against a Confluence wiki containing roughly 2,000 pages, edge cases and unforeseen scenarios may remain. Please report any issues.

## Install

```xml
<dependency>
    <groupId>dev.nthings</groupId>
    <artifactId>adf4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Build

```bash
./mvnw verify
./mvnw package -Pnative -pl adf4j-cli -am -DskipTests
./mvnw package -Pwasm -pl adf4j-wasm -am -DskipTests
```

## Convert ADF

```java
import dev.nthings.adf4j.AdfToMarkdown;

AdfToMarkdown converter = AdfToMarkdown.create();
String markdown = converter.toMarkdown(adfJson);
```

Build one `AdfToMarkdown` and reuse it across documents and threads.

## CLI

Download the archive for your platform from the [latest release](https://github.com/thiagogcm/adf4j/releases/latest), extract it, and run `adf4j-cli`.

```bash
adf4j convert doc.adf.json
cat doc.adf.json | adf4j convert -t "My Page" -o out.md
adf4j analyze --select referencedFileIds,outline doc.adf.json
adf4j validate --fail-on-warning doc.adf.json
```

Input comes from a file argument or stdin. Stdout contains only the requested output, while diagnostics go to stderr. Run `adf4j <command> --help` for command-specific flags.

## WASM

```bash
npm install @nthings.dev/adf4j-wasm
```

```js
import { loadAdf4j } from '@nthings.dev/adf4j-wasm';

const adf4j = await loadAdf4j();
const markdown = adf4j.convert(adfJsonString);
const result = adf4j.convertJson(adfJsonString);
```

The npm package includes the GraalVM Web Image loader and compiled `.wasm` module for Node.js and
browser bundlers.

## Documentation

| Doc                                        | Use it for                                                               |
| ------------------------------------------ | ------------------------------------------------------------------------ |
| [Getting started](docs/getting-started.md) | Install, convert a first document, inspect results, and run the CLI.     |
| [Guide](docs/guide.md)                     | Resolver patterns, macros, attachments, options, and AST usage.          |
| [Reference](docs/reference.md)             | Complete option, mapping, diagnostic, safety, CLI, and exit-code tables. |
| [Architecture](docs/architecture.md)       | Internal design for contributors and advanced integrators.               |

The local [ADF spec snapshot](docs/spec/README.md) mirrors Atlassian's format for offline reference.

## Release

Releases use JReleaser and GitHub Actions. See [RELEASE.md](RELEASE.md).

## License

[Apache-2.0](LICENSE).
