# Getting started

This is the shortest path from an ADF JSON document to GitHub-Flavored Markdown. It covers the one-liner, a worked example, how to read a conversion result, and the CLI. Once you have a first conversion working, the [guide](./guide.md) teaches the concepts and recipes (resolvers, page links, custom macros), and the [reference](./reference.md) lists every option, the full mapping matrix, and the complete CLI.

## Requirements

`adf4j` requires **JDK 25**. The library is a Java Platform Module System (JPMS) module named `dev.nthings.adf4j`.

```xml
<dependency>
  <groupId>dev.nthings</groupId>
  <artifactId>adf4j</artifactId>
  <version>1.0.0</version>
</dependency>
```

If you consume it as a module, require it in your `module-info.java`:

```java
requires dev.nthings.adf4j;
```

The public API is in six packages — `dev.nthings.adf4j` (the entry point) plus `.options`, `.result`, `.metadata`, `.confluence`, and `.ast`. Everything under `.internal` is encapsulated by JPMS and not part of the contract.

> **Where ADF comes from.** Confluence Cloud returns a page's ADF as `body.atlas_doc_format.value` from its content REST API (request `body-format=atlas_doc_format`), and Jira issue rich-text fields are ADF too. Confluence Server/Data Center stores storage-format XHTML, not ADF.

## Quick start

One line converts a document with default options:

```java
import dev.nthings.adf4j.AdfToMarkdown;

String markdown = AdfToMarkdown.create().toMarkdown(adfJson);
```

`AdfToMarkdown` is immutable and thread-safe. It compiles its pipeline once per instance, so **build one converter and reuse it** across documents and threads — keep it as a singleton or injected bean rather than calling `create()` per call. When options must vary per document, reuse the same converter and pass options per call (see the [guide](./guide.md#per-call-options-vs-bound-options)); do not construct a new converter each time.

## A worked example

Here is a small ADF document and the Markdown it produces with default options (the output below is the library's actual output):

**Input (ADF JSON):**

```json
{
  "type": "doc",
  "version": 1,
  "content": [
    { "type": "heading", "attrs": { "level": 1 },
      "content": [ { "type": "text", "text": "Release Notes" } ] },
    { "type": "paragraph", "content": [
      { "type": "text", "text": "See the " },
      { "type": "text", "text": "migration guide",
        "marks": [ { "type": "link", "attrs": { "href": "https://example.com/guide" } } ] },
      { "type": "text", "text": " before upgrading." } ] },
    { "type": "panel", "attrs": { "panelType": "warning" },
      "content": [ { "type": "paragraph",
        "content": [ { "type": "text", "text": "This release drops Java 21." } ] } ] },
    { "type": "bulletList", "content": [
      { "type": "listItem", "content": [ { "type": "paragraph",
        "content": [ { "type": "text", "text": "Faster parsing" } ] } ] },
      { "type": "listItem", "content": [ { "type": "paragraph", "content": [
        { "type": "text", "text": "New " },
        { "type": "text", "text": "analyze()", "marks": [ { "type": "code" } ] },
        { "type": "text", "text": " API" } ] } ] }
    ] }
  ]
}
```

**Output (GitHub-Flavored Markdown):**

```markdown
# Release Notes

See the [migration guide](https://example.com/guide) before upgrading.

> [!WARNING]
> This release drops Java 21.

- Faster parsing
- New `analyze()` API
```

Note the GFM-idiomatic choices: the Confluence *warning* panel becomes a GFM alert (`> [!WARNING]`), the link renders inline, and the `code` mark becomes a backtick span. The [reference mapping matrix](./reference.md#adf--markdown-mapping) shows how every construct maps.

## Inspect the result

`toMarkdown(...)` returns just the body string. To see whether the conversion lost anything, call `convert(...)`, which returns a `MarkdownResult` carrying the body, extracted metadata, and a list of diagnostics:

```java
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;

AdfToMarkdown converter = AdfToMarkdown.create();
MarkdownResult result = converter.convert(adfJson);

String body = result.body();

if (result.wasLossy()) {                       // log is your own SLF4J logger
    for (Diagnostic diagnostic : result.diagnostics()) {
        if (diagnostic.severity() != Diagnostic.Severity.INFO) {
            log.warn("Conversion issue [{}]: {}", diagnostic.code(), diagnostic.message());
        }
    }
}
```

`wasLossy()` is `true` only when a diagnostic is `WARNING` or `ERROR` — content dropped or altered, or a structural parse failure. It deliberately ignores *by-design, options-driven* outcomes (placeholders when you set no resolver, dropped visual marks, the table HTML fallback). Gate real loss on `wasLossy()`, not on "any diagnostic present" — some are informational.

`MarkdownResult` also exposes `metadata()` (the document's references, attachments, and outline) and `unresolved()` (lookups a configured resolver declined). The [guide](./guide.md#results-diagnostics-and-lossiness) covers the full result and diagnostics model; the [reference](./reference.md#diagnostics) lists the diagnostic codes.

## Use the CLI

The `adf4j` CLI ships as a native executable per platform. Download the archive for your OS from the [latest GitHub release](https://github.com/thiagogcm/adf4j/releases/latest), extract it, and run the `adf4j-cli` binary. The three subcommands each map onto one library method:

```bash
adf4j convert doc.adf.json                                   # Markdown body on stdout
adf4j analyze --select referencedFileIds,outline doc.adf.json  # references/outline as JSON
adf4j validate --fail-on-warning doc.adf.json                # diagnostics; exit code reflects validity
```

Input comes from the `<input-file>` argument or, when none is given, stdin — so the CLI composes in a pipeline:

```bash
cat doc.adf.json | adf4j convert -t "My Page" -o out.md
```

Stdout carries only the deliverable (the Markdown body, or JSON); diagnostics and warnings go to stderr, so `adf4j convert doc.adf.json > out.md` stays clean. The `-o` flag writes the output file atomically.

The [reference CLI section](./reference.md#cli) documents every flag, the resolver-flag schemas, and the exit codes.

## Where to go next

- **[Guide](./guide.md)** — the mental model and task recipes: resolving media and attachments to real URLs, rewriting inter-page links, expanding page-tree/excerpt macros, rendering custom macros, and tuning tables and formatting.
- **[Reference](./reference.md)** — the lookup tables: the full `MarkdownOptions`, the complete ADF-to-Markdown mapping matrix, diagnostics, the lossy/by-design catalogue, URL safety, and the full CLI.
- **[Architecture](./architecture.md)** — the internals for contributors: module layout, the parse/analyze/render pipeline, the AST type model, and the extensibility design.
