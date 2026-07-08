# Getting started

This page gets one ADF JSON document converted to GitHub-Flavored Markdown. For resolver recipes and deeper concepts, use the [guide](./guide.md). For complete tables of options, mappings, diagnostics, and CLI flags, use the [reference](./reference.md).

## Requirements

adf4j requires **JDK 25**. The library is a JPMS module named `dev.nthings.adf4j`.

```xml
<dependency>
  <groupId>dev.nthings</groupId>
  <artifactId>adf4j</artifactId>
  <version>1.0.0</version>
</dependency>
```

If your application uses modules:

```java
requires dev.nthings.adf4j;
```

The supported API is in `dev.nthings.adf4j` plus `.options`, `.result`, `.metadata`, `.confluence`, and `.ast`. Packages under `.internal` are not API.

Confluence Cloud exposes ADF at `body.atlas_doc_format.value` when the REST API request uses `body-format=atlas_doc_format`. Jira rich-text fields also use ADF. Confluence Server and Data Center use storage-format XHTML instead.

## Convert a document

```java
import dev.nthings.adf4j.AdfToMarkdown;

AdfToMarkdown converter = AdfToMarkdown.create();
String markdown = converter.toMarkdown(adfJson);
```

`AdfToMarkdown` is immutable and thread-safe. Create one converter and reuse it. When resolver state differs by document, pass per-call `MarkdownOptions` instead of creating a new converter.

## Example

**Input ADF:**

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

**Output Markdown:**

```markdown
# Release Notes

See the [migration guide](https://example.com/guide) before upgrading.

> [!WARNING]
> This release drops Java 21.

- Faster parsing
- New `analyze()` API
```

The warning panel becomes a GFM alert, the link renders inline, and the code mark becomes a backtick span. The full mapping table is in the [reference](./reference.md#adf-to-markdown-mapping).

## Inspect diagnostics and metadata

`toMarkdown(...)` returns only the body string. Use `convert(...)` when you need diagnostics, metadata, or unresolved references:

```java
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.MarkdownResult;

MarkdownResult result = converter.convert(adfJson);
String body = result.body();

if (result.wasLossy()) {
  for (Diagnostic diagnostic : result.diagnostics()) {
    if (diagnostic.severity() != Diagnostic.Severity.INFO) {
      log.warn("Conversion issue [{}]: {}", diagnostic.code(), diagnostic.message());
    }
  }
}
```

`wasLossy()` is true when a diagnostic is `WARNING` or `ERROR`. Informational diagnostics do not make a result lossy, and expected option-driven behavior such as unresolved media placeholders is not counted as lossy.

`result.metadata()` exposes references, attachments, excerpts, and the heading outline. `result.unresolved()` lists resolver lookups that stayed unresolved during this render.

## Use the CLI

Download the native executable for your OS from the [latest GitHub release](https://github.com/thiagogcm/adf4j/releases/latest), extract it, and run `adf4j-cli`.

```bash
adf4j convert doc.adf.json
adf4j analyze --select referencedFileIds,outline doc.adf.json
adf4j validate --fail-on-warning doc.adf.json
```

Input comes from `<input-file>` or stdin:

```bash
cat doc.adf.json | adf4j convert -t "My Page" -o out.md
```

Stdout contains only the requested output. Diagnostics and warnings go to stderr, so shell redirection stays clean. The `-o` flag writes output atomically.

## Next

- [Guide](./guide.md): resolver patterns, macros, attachments, options, and AST usage.
- [Reference](./reference.md): complete option, mapping, diagnostic, safety, CLI, and exit-code tables.
- [Architecture](./architecture.md): internals for contributors and advanced integrators.
