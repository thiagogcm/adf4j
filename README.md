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

This repo uses [`just`](https://just.systems) as its task runner. Run `just` to list recipes; the
same recipes back both local development and CI. Repository-level Node tasks require Node 24 or
newer.

```bash
just verify   # compile, test, coverage gate, and format checks
just native   # GraalVM native CLI executable
just wasm     # GraalVM WASM web image
just chrome-ext # unpacked Confluence Cloud Chrome extension
```

Each recipe wraps the Maven wrapper, so `./mvnw verify` and friends still work directly.

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

One entry point runs in Node.js, Bun, Deno, and the browser. The npm package bundles the GraalVM Web
Image loader and the compiled `.wasm`. In Vite, add the shipped plugin (`import adf4jWasm from
'@nthings.dev/adf4j-wasm/vite'`) or set `optimizeDeps.exclude: ['@nthings.dev/adf4j-wasm']`. See the
[package README](adf4j-wasm/src/npm/README.md) for bundler, runtime, and self-hosting details.

## Chrome extension

`adf4j-chrome-ext` is a local Manifest V3 extension for Confluence Cloud. It adds a floating
`Copy as markdown` button to detected Confluence pages, fetches the published page body as
`atlas_doc_format`, converts it with `adf4j-wasm`, and writes Markdown to the clipboard.

Build the unpacked extension with `just chrome-ext`, then load `adf4j-chrome-ext/dist` from
`chrome://extensions`.

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
