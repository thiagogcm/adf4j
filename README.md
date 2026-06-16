# adf4j

Atlassian Document Format (ADF) processing for Java. Convert ADF JSON — the rich-content format Confluence and Jira store — to GitHub-Flavored Markdown, extract its references/attachments/outline, and validate it. Use it as a Java library or a standalone CLI.

- **Library** (`dev.nthings:adf4j`) — published to Maven Central. Immutable, thread-safe, dependency-light, and I/O-free: it never reaches out to Confluence or a CDN, delegating every URL/page lookup back to you.
- **CLI** — distributed as GraalVM native executables (Linux, macOS, Windows) plus a WASM build, attached to each [GitHub release](https://github.com/thiagogcm/adf4j/releases).

Requires **Java 25**.

## Library

```xml
<dependency>
    <groupId>dev.nthings</groupId>
    <artifactId>adf4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

One line converts a document with default options:

```java
import dev.nthings.adf4j.AdfToMarkdown;

String markdown = AdfToMarkdown.create().toMarkdown(adfJson);
```

A Confluence *warning* panel becomes a GFM alert and a `link` mark renders inline:

```markdown
# Release Notes

See the [migration guide](https://example.com/guide) before upgrading.

> [!WARNING]
> This release drops Java 21.
```

`AdfToMarkdown` is immutable and thread-safe — build one converter, reuse it across documents and threads. For metadata and diagnostics, use `convert(json)` (returns a `MarkdownResult`); to plan attachment fetches without rendering, use `analyze(json)`.

## CLI

Download the archive for your platform from the [latest release](https://github.com/thiagogcm/adf4j/releases/latest), extract it, and run the `adf4j-cli` binary. Three subcommands, each mapping to one library method; input from a file argument or stdin:

```bash
adf4j convert doc.adf.json                        # ADF → Markdown body on stdout
cat doc.adf.json | adf4j convert -t "My Page" -o out.md
adf4j analyze --select referencedFileIds,outline doc.adf.json   # references/outline as JSON
adf4j validate --fail-on-warning doc.adf.json     # parse-check; exit code reflects validity
```

Run `adf4j <command> --help` for the full options. The WASM build (`adf4j-wasm-<version>.zip`) runs the converter on a JavaScript host (e.g. Node) with no JVM.

## Documentation

| Doc                                            | What it covers                                                                                                                                                                                                      |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **[Getting started](docs/getting-started.md)** | The shortest path: install, your first conversion, reading the result, and the CLI.                                                                                                                                 |
| **[Guide](docs/guide.md)**                     | The mental model (the parse → analyze → render pipeline, results & lossiness, the resolver model) and the task recipes — resolving media and links, expanding macros, custom extensions, the analyze-first pattern. |
| **[Reference](docs/reference.md)**             | The lookup tables: every `MarkdownOptions`, the full ADF → Markdown mapping, diagnostics, URL safety, and the complete CLI (flags, resolver schemas, exit codes).                                                   |
| **[Architecture](docs/architecture.md)**       | The internals for contributors: module layout, the conversion pipeline, the AST type model, and the extensibility and error-handling designs.                                                                       |

The local [ADF spec snapshot](docs/spec/README.md) mirrors the upstream Atlassian format for offline reference.

## Building from source

```bash
./mvnw verify                                           # build + test + coverage gate
./mvnw package -Pnative -pl adf4j-cli -am -DskipTests   # native CLI for the current OS
./mvnw package -Pwasm -pl adf4j-wasm -am -DskipTests    # WASM build
```

## Releasing

Releases are automated with JReleaser and GitHub Actions. See [RELEASE.md](RELEASE.md).

## License

[Apache-2.0](LICENSE).
