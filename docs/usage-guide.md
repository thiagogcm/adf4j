# adf4j — Usage Guide

`adf4j` converts **Atlassian Document Format (ADF)** JSON — the format Confluence and Jira store rich content in — into **GitHub-Flavored Markdown**. This guide is a task-oriented tour: start with one line, then layer in the features you need.

It complements two reference documents. For the complete option-by-option table and the full catalogue of lossy/by-design behaviours, see [Markdown Conversion](./markdown-conversion.md). For how the library is built internally, see the [Architecture](./architecture.md) document.

## Table of contents

- [Requirements and coordinates](#requirements-and-coordinates)
- [Quick start](#quick-start)
- [A worked example](#a-worked-example)
- [Choosing an entry point](#choosing-an-entry-point)
- [Configuring a conversion](#configuring-a-conversion)
- [Inspecting the result](#inspecting-the-result)
- [Planning attachment fetches before rendering](#planning-attachment-fetches-before-rendering)
- [Resolving media and attachments to real URLs](#resolving-media-and-attachments-to-real-urls)
- [Rewriting inter-page links](#rewriting-inter-page-links)
- [Expanding page-tree and children macros](#expanding-page-tree-and-children-macros)
- [Rendering custom macros and extensions](#rendering-custom-macros-and-extensions)
- [Controlling unknown nodes](#controlling-unknown-nodes)
- [Tables, visual marks, and other formatting toggles](#tables-visual-marks-and-other-formatting-toggles)
- [Working with the parsed AST](#working-with-the-parsed-ast)
- [Per-call options vs. bound options](#per-call-options-vs-bound-options)
- [Using the CLI](#using-the-cli)
- [ADF → Markdown reference](#adf--markdown-reference)
- [Troubleshooting](#troubleshooting)
- [Edge cases and robustness](#edge-cases-and-robustness)

## Requirements and coordinates

`adf4j` requires **JDK 25**. It is a Java Platform Module System (JPMS) module, `dev.nthings.adf4j`.

```xml
<dependency>
  <groupId>dev.nthings</groupId>
  <artifactId>adf4j</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

If you consume it as a module, require it in your `module-info.java`:

```java
requires dev.nthings.adf4j;
```

The public API lives in six packages: `dev.nthings.adf4j` (the entry point), `…options` (configuration and the caller-implemented hooks), `…result`, `…metadata`, `…confluence`, and `…ast`. Everything under `…internal` is encapsulated and not part of the contract.

## Quick start

```java
import dev.nthings.adf4j.AdfToMarkdown;

String markdown = AdfToMarkdown.create().toMarkdown(adfJson);
```

`create()` uses default options. The converter is immutable and thread-safe — for anything beyond a one-off conversion, build it once and reuse it.

## A worked example

To make the transformation concrete, here is a small ADF document and the Markdown it produces with default options (the output below is the library’s actual output, not an approximation):

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

Note the GFM-idiomatic choices: the Confluence *warning* panel becomes a GFM alert (`> [!WARNING]`), the link is rendered inline, and the `code` mark becomes a backtick span. The [reference matrix](#adf--markdown-reference) below lists how every construct maps.

## Choosing an entry point

`AdfToMarkdown` is the single entry point; its methods select how far the pipeline runs and what you get back.

```java
import dev.nthings.adf4j.AdfToMarkdown;

// Build once (e.g. as a singleton / injected bean), reuse everywhere.
AdfToMarkdown converter = AdfToMarkdown.create();

String markdown = converter.toMarkdown(adfJson);
```

> **Reuse, don’t rebuild.** A converter compiles its pipeline once. Create one instance for the lifetime of your application and share it across threads. When configuration must change per document, keep the one converter and pass options per call (see [Per-call options](#per-call-options-vs-bound-options)) rather than constructing a new converter each time.

| Method | Runs | Returns | Use when |
| --- | --- | --- | --- |
| `toMarkdown(json)` | parse → analyze → render | `String` | You only want the Markdown body |
| `convert(json)` | parse → analyze → render | `MarkdownResult` | You want body **plus** metadata and diagnostics |
| `analyze(json)` | parse → analyze | `ContentMetadata` | You want references/outline **without** rendering |
| `parse(json)` | parse | `ParseResult` | You want the typed AST and structural diagnostics |

`convert` also accepts the `ParseResult` itself — parse once, render many times, with the parse issues carried into each result (see [Working with the parsed AST](#working-with-the-parsed-ast)). `convert` and `analyze` also accept an already-parsed `AdfDocument`; `toMarkdown` is a String-only convenience — use `convert(doc).body()` for a parsed document. All of `convert`, `analyze`, and `toMarkdown` accept a per-call `MarkdownOptions`.

## Configuring a conversion

Configuration is an immutable `MarkdownOptions` value. Build it with the fluent builder or with `defaults()` plus `withX(...)` withers — both are forward-compatible. Bind it to a converter with `with(...)`:

```java
import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.TableFallback;

MarkdownOptions options = MarkdownOptions.builder()
    .htmlVisualMarks(true)
    .tableFallback(TableFallback.GFM_PROMOTE_FIRST_ROW)
    .collapseHardBreaks(true)
    .build();

AdfToMarkdown converter = AdfToMarkdown.with(options);
```

The equivalent with withers, starting from defaults:

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withHtmlVisualMarks(true)
    .withCollapseHardBreaks(true);
```

> `MarkdownOptions` has no public constructor — the builder and the withers are the API, and both stay source-compatible as options are added. An existing instance can be varied with `toBuilder()`.

The rest of this guide shows each feature in isolation. For the canonical reference of every option, its default, and its exact effect, see the [options table](./markdown-conversion.md#options-markdownoptions).

## Inspecting the result

`convert(...)` returns a `MarkdownResult` carrying the body, the extracted `ContentMetadata`, a list of diagnostics, and the `unresolved()` lookups this render's resolvers declined. Use `wasLossy()` to decide whether the conversion lost anything that matters:

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

`wasLossy()` is true only when a diagnostic is `WARNING` or `ERROR` — content that was dropped or altered, or a structural parse failure. It deliberately ignores *by-design, options-driven* outcomes (placeholders when you set no resolver, dropped visual marks, the table HTML fallback) — see the [lossy and by-design behaviours](./markdown-conversion.md#lossy-and-by-design-behaviours) for the full catalogue. Gate on `wasLossy()` (or on each diagnostic’s `severity()`), not on “any diagnostic present”.

`result.unresolved()` reports, per conversion, what the active resolvers were asked for and declined — no decorator-wrapping of your resolvers needed: `pageIds()` are the page node ids a configured `pageLinkResolver` returned nothing for, and `pageTreeRefs()` are the `pagetree`/`children` macros that fell back to their placeholder token. An empty `unresolved()` means this render left no declined-lookup artifacts in the output.

## Planning attachment fetches before rendering

A powerful pattern is to **analyze first, fetch, then render**. `analyze(...)` runs parse + analyze without rendering and returns `ContentMetadata` — including every attachment the document references. `referencedFileIds()` gives you exactly the binaries to fetch:

```java
import dev.nthings.adf4j.metadata.ContentMetadata;
import java.util.Set;

ContentMetadata metadata = converter.analyze(adfJson);

// Fetch only what this document actually needs.
Set<String> fileIds = metadata.referencedFileIds();
Map<String, String> resolvedUrls = myStorage.fetchAll(fileIds);

// Now render with a resolver that consults what you fetched.
MarkdownOptions options = MarkdownOptions.defaults()
    .withMediaResolver(attrs -> resolvedUrls.get(attrs.id()));

String markdown = converter.toMarkdown(adfJson, options);
```

> **Attachment macros need a context to be counted.** Media-node file ids are always collected, but a Confluence attachment macro (e.g. *view file* / `viewpdf`) contributes its file id to `referencedFileIds()` **only when its title resolves against a `ConfluenceRenderContext`**. If your documents use attachment macros, analyze with the context supplied — `converter.analyze(adfJson, optionsWithContext)` — or those ids will be silently absent from the fetch plan. See [Resolving media and attachments](#resolving-media-and-attachments-to-real-urls) for building the context.

`ContentMetadata` also exposes the document’s outbound page links (`pageRefs()`), its `pagetree`/`children` macro occurrences (`pageTreeRefs()` — each with the macro kind and its normalized root), external links (`externalRefs()`), mentions (`mentionRefs()`), and a heading outline (`outline()` — each `HeadingReference` carries `level`, `text`, and a unique `anchor`). This is handy for building a link graph, a search index, or a navigation sidebar without rendering anything — and `pageRefs()` plus `pageTreeRefs()` classify a document up front: both empty means it is self-contained, needing no page hierarchy or link resolution to render fully.

## Resolving media and attachments to real URLs

ADF media and Confluence attachment macros reference content by **id, not URL** — the binary lives elsewhere. Without a resolver, these render to inert placeholder destinations such as `media:<collection>/<id>` (e.g. `media:contentId-42/abc-123`) and `attachment:<fileId>` (e.g. `[PDF: guide.pdf](attachment:file-1)`), which you can recognize and grep for. Supply a resolver to turn them into real links.

**Media** (images and files embedded as ADF media nodes):

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withMediaResolver(attrs -> {
        // attrs exposes id(), collection(), fileName(), mimeOrType(), etc.
        if (attrs.id() == null) {
            return null;                 // returning null/blank keeps the placeholder
        }
        return "https://cdn.example.com/files/" + attrs.id();
    });
```

**Confluence attachment macros** (e.g. *view file* / `viewpdf`). These resolve by title against a `ConfluenceRenderContext`, then through your `AttachmentResolver`:

```java
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.List;

ConfluenceRenderContext context = ConfluenceRenderContext.empty()
    .withAttachmentReferences(List.of(
        new AttachmentReference("file-123", "Q3 Report.pdf", "application/pdf")));

MarkdownOptions options = MarkdownOptions.defaults()
    .withConfluenceContext(context)
    .withAttachmentResolver(ref -> "https://cdn.example.com/files/" + ref.fileId());
```

The Confluence context supplies the attachment table the analyze phase uses to recognize attachment macros by name (and to populate `ContentMetadata`); the `attachmentResolver` turns each resolved reference into a URL at render time.

## Rewriting inter-page links

Confluence documents link to other pages by internal id. To rewrite those links to wherever the pages landed in your output (a static site, a wiki, an archive), supply a `PageLinkResolver` keyed by page node id:

```java
import java.util.Map;

Map<String, String> pageUrls = Map.of(
    "98765", "/docs/onboarding.html",
    "98766", "/docs/security.html");

MarkdownOptions options = MarkdownOptions.defaults()
    .withPageLinkResolver(pageNodeId -> pageUrls.get(pageNodeId));  // null → keep original href
```

The resolver is consulted for inline page links, page smart-cards, **and** page-tree entries. The page node ids it receives are the same ids surfaced in `ContentMetadata.pageRefs()`, so you can discover them up front with `analyze(...)`.

## Expanding page-tree and children macros

The Confluence `pagetree` and `children` macros list pages that Confluence assembles *server-side* from the space hierarchy — the ADF carries only the macro reference, not the page list. Without a resolver they render to a `{{pagetree}}` / `{{children}}` placeholder token. Supply a `PageTreeResolver` to expand them, returning the descendants as depth-tagged entries:

```java
import dev.nthings.adf4j.metadata.PageTreeMacro;
import dev.nthings.adf4j.options.PageTreeEntry;
import java.util.List;

MarkdownOptions options = MarkdownOptions.defaults()
    .withPageLinkResolver(pageUrls::get)   // entry pageNodeIds route through this for their links
    .withPageTreeResolver(reference -> {
        // reference.macro() is PAGETREE or CHILDREN; reference.root() is the starting page id
        // (null when absent/@self); reference.depth() and reference.all() are the standard macro
        // parameters pre-parsed; reference.parameters() holds the raw map (startDepth, ...).
        boolean wholeTree = reference.macro() == PageTreeMacro.PAGETREE || reference.all();
        return myHierarchy.descendants(reference.root(), wholeTree, reference.depth().orElse(1)).stream()
            .map(p -> new PageTreeEntry(p.depth(), p.title(), p.id()))
            .toList();
    });
```

Each entry’s `depth` (0-based) drives indentation, `title` becomes the visible label (Markdown-escaped for you), and `pageNodeId` is routed through your `pageLinkResolver` to produce the link — an entry whose id does not resolve renders as plain text. A non-null return is authoritative: an **empty list means "this page has no descendants"** and renders as nothing, keeping the output clean. Returning `null`, or throwing, declines and leaves the placeholder token in place (the base `{{pagetree}}` / `{{children}}`, or a parameterized `{{pagetree:<root>}}` / `{{children:<depth>}}` form); each macro that fell back this way is listed in `MarkdownResult.unresolved().pageTreeRefs()`.

## Rendering custom macros and extensions

Macros without a built-in renderer become a labelled placeholder and a lossy diagnostic. To render your own (or override a built-in), register one or more `ExtensionRenderer`s. They are consulted in order, before the built-in Confluence macros, and the first to return non-null wins (an empty string is a valid answer that suppresses the macro's output):

```java
import dev.nthings.adf4j.options.ExtensionRenderer;
import java.util.List;

ExtensionRenderer infoPanel = ctx -> {
    // Match on type AND key — keys are only unique within an extension namespace.
    if (!"com.atlassian.confluence.macro.core".equals(ctx.extensionType())
            || !"info".equals(ctx.extensionKey())) {
        return null;                      // defer to the next renderer / built-in
    }
    String body = ctx.parameter("body");  // read named macro parameters
    return "> ℹ️ " + body;
};

MarkdownOptions options = MarkdownOptions.defaults()
    .withExtensionRenderers(List.of(infoPanel));
```

The `ExtensionContext` exposes `extensionType()`, `extensionKey()`, `text()`, and the macro `parameters()` (with a `parameter(name)` convenience). Two cautions:

- **Output is emitted verbatim — it is not sanitized.** If you interpolate untrusted macro parameters, escape them yourself.
- **A thrown `RuntimeException` is caught and logged**, and the engine falls back to its built-in handling rather than aborting the conversion. The same isolation applies to every resolver above.

## Controlling unknown nodes

ADF evolves; a document may contain node types this version of the library does not model. `UnknownNodePolicy` decides what happens to them:

```java
import dev.nthings.adf4j.options.UnknownNodePolicy;

MarkdownOptions options = MarkdownOptions.defaults()
    .withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW);
```

| Policy | Effect |
| --- | --- |
| `PLACEHOLDER` *(default)* | Emit a visible `[Unsupported: <type>]` token |
| `SKIP` | Drop the node entirely (still logged as a diagnostic) |
| `PRESERVE_RAW` | Emit the node’s original JSON (fenced block for blocks, inline code for inlines) — round-trippable |
| `FAIL` | Throw `IllegalStateException` — use in pipelines that must not silently lose content |

Unknown *marks* (decorations on text) have no standalone form: they are always dropped and reported as a `WARNING`, regardless of policy.

## Tables, visual marks, and other formatting toggles

A few common formatting controls (see the [full table](./markdown-conversion.md#options-markdownoptions) for all of them):

```java
import dev.nthings.adf4j.options.TableFallback;

MarkdownOptions options = MarkdownOptions.defaults()
    // How a header-less table becomes GFM (default promotes the first row to a header):
    .withTableFallback(TableFallback.GFM_EMPTY_HEADER)
    // Preserve colour/background/border/font-size/alignment as inline HTML instead of dropping them:
    .withHtmlVisualMarks(true)
    // Emit the non-GFM {width= height=} suffix after images:
    .withImageSizeAttributes(true)
    // Render Shift+Enter hard breaks as soft newlines (no trailing whitespace):
    .withCollapseHardBreaks(true)
    // Backslash-escape literal ( and ) in text (off by default; the parentheses are inert):
    .withEscapeParentheses(true)
    // Prepend a level-1 title heading above the body:
    .withDocumentTitle("Release Notes");
```

Tables that pipe syntax genuinely cannot express — colspan/rowspan, a number column, block-level cell content, or headers in a non-canonical position — always fall back to an HTML `<table>` regardless of `tableFallback`. A plain table renders as native GFM, while one with a `colspan` falls back:

```markdown
| a1  | a2  |
| --- | --- |
| b1  | b2  |
```

```html
<table><tr><th colspan="2">Wide header</th></tr><tr><td rowspan="2">group</td><td>left</td></tr><tr><td>right</td></tr></table>
```

The `documentTitle` is render-only (it is not reflected in `ContentMetadata`) and is not de-duplicated against a heading the body may already contain. It is emitted even when the body is empty, blank, or fails to parse, so producing a titled-but-empty document requires no synthetic empty ADF input.

## Working with the parsed AST

To inspect structure or convert the same document more than once without re-parsing, parse once and reuse the immutable `ParseResult`. `convert(ParseResult, ...)` carries the parse issues into each result's diagnostics and handles blank/invalid input the same way `convert(json)` does — the natural fit for rendering one document N times under different options or resolver states:

```java
import dev.nthings.adf4j.result.ParseResult;

ParseResult parsed = converter.parse(adfJson);

// Render the same parsed tree under different resolver states, paying the JSON parse once.
MarkdownResult asOfJanuary = converter.convert(parsed, januaryOptions);
MarkdownResult asOfToday = converter.convert(parsed, todayOptions);
```

For direct AST work, the parsed `AdfDocument` itself is also accepted by `analyze`/`convert` (note these overloads carry no parse diagnostics — prefer the `ParseResult` overload when you want them):

```java
import dev.nthings.adf4j.ast.AdfDocument;

if (parsed.validAdfRoot()) {
    AdfDocument doc = parsed.document();

    ContentMetadata metadata = converter.analyze(doc);
    String markdown = converter.convert(doc).body();   // reuses the same parsed tree
}
```

The AST is a sealed hierarchy of immutable records under `dev.nthings.adf4j.ast`: `AdfDocument` holds blocks (`AdfBlock`), blocks hold inlines (`AdfInline`), and text/media carry marks (`AdfMark`). You can pattern-match over them — but keep a `default` arm (or handle the `Unknown*` variants), because the permitted type lists grow as ADF evolves:

```java
import dev.nthings.adf4j.ast.*;

for (AdfBlock block : doc.content()) {
    String summary = switch (block) {
        case Heading h -> "H" + h.level();
        case Paragraph p -> "paragraph";
        case Table t -> "table";
        default -> block.getClass().getSimpleName();
    };
}
```

## Per-call options vs. bound options

A converter has *bound* options (from `create()`/`with(...)`). The `convert`, `analyze`, and `toMarkdown` overloads that take a `MarkdownOptions` argument **override** the bound options for that call only. This is the supported way to handle documents whose context differs — per-page attachment tables, media base URLs, or page-link maps — from one reusable converter:

```java
AdfToMarkdown converter = AdfToMarkdown.create();   // build once

for (Page page : pages) {
    MarkdownOptions perPage = MarkdownOptions.defaults()
        .withConfluenceContext(ConfluenceRenderContext.empty()
            .withAttachmentReferences(page.attachments()))
        .withMediaResolver(attrs -> page.mediaUrl(attrs.id()));

    String md = converter.toMarkdown(page.adfJson(), perPage);  // same converter, per-call options
}
```

This avoids the anti-pattern of constructing a new `AdfToMarkdown` for every document.

## Using the CLI

The `adf4j-cli` module wraps the library for shell use. It reads ADF JSON from a file argument or stdin and writes Markdown to stdout or a file:

```
adf4j-cli [-o <file>] [<input-file>]

  -o, --output=FILE            Write output to FILE instead of stdout
  -t, --title=TITLE            Prepend TITLE as a level-1 (# ) heading
  -c, --collapse-hard-breaks   Render hard breaks as soft breaks (no trailing spaces)
  -p, --escape-parentheses     Backslash-escape literal ( and ) in text (off by default)
  -h, --help                   Show help
```

```sh
# From a file to stdout
adf4j-cli document.adf.json

# From stdin to a file, with a title
cat document.adf.json | adf4j-cli -t "My Page" -o out.md
```

The examples use the `adf4j-cli` executable produced by the GraalVM `native` build profile (a WebAssembly build is available via the `wasm` profile). The module’s jar is **not** a standalone fat jar, so to run it on the JVM you must put its dependencies (the `adf4j` library, Jackson, CommonMark, jsoup, SLF4J, JLine) on the classpath/module-path yourself.

It exits `0` on success and `1` on a usage error or a missing input file. Note that malformed ADF does **not** cause a non-zero exit: the conversion never throws on bad input, so the CLI prints an empty document and still exits `0`.

## ADF → Markdown reference

A scannable map of how each ADF construct is rendered with default options. Anchors, autolinks, and escaping are applied automatically.

**Block constructs**

| ADF construct | Rendered as |
| --- | --- |
| `heading` (levels 1–6) | `#` … `######` (levels > 6 clamped to 6); gains an `<a id>` anchor when a `toc` macro references its level |
| `paragraph` | a text block |
| `bulletList` / `orderedList` | `-` / `1.` items (nesting indented; `order`/`start` honoured) |
| `taskList` | `- [x]` (DONE) / `- [ ]` (TODO) |
| `decisionList` | `- \[decision:DECIDED\] …` / `- \[decision:UNDECIDED\] …` |
| `blockquote` | `>` quote (blank `>` lines between paragraphs) |
| `codeBlock` | fenced ` ```lang ` block (fence widened to avoid collisions) |
| `panel` | GFM alert: info/note → `> [!NOTE]`, warning → `> [!WARNING]`, error → `> [!CAUTION]`, success/tip → `> [!TIP]` |
| `rule` | `---` thematic break |
| `table` | native GFM pipe table, or an HTML `<table>` fallback (see [Tables](#tables-visual-marks-and-other-formatting-toggles)) |
| `expand` / `nestedExpand` | `<details><summary>…</summary>…</details>` |
| `layoutSection` / `layoutColumn` | columns flattened into sequential blocks |
| `mediaSingle` / `mediaGroup` / `media` | image `![alt](url)` or file link `[label](url)`; a `media:<collection>/<id>` placeholder destination without a `mediaResolver` |

**Inline constructs and marks**

| ADF construct | Rendered as |
| --- | --- |
| `text` | plain text, with Markdown punctuation escaped (literal `(`/`)` left unescaped unless `escapeParentheses` is enabled) |
| `strong` / `em` / `strike` / `code` | `**bold**` / `*italic*` / `~~strike~~` / `` `code` `` |
| `underline` / `subsup` | HTML `<u>…</u>` / `<sub>…</sub>`, `<sup>…</sup>` |
| `link` | `[text](url)` with the URL scheme-sanitized |
| visual marks (`textColor`, `backgroundColor`, `border`, `fontSize`, `alignment`) | dropped by default; HTML `<span style>` / `<div align>` with `htmlVisualMarks` enabled |
| `hardBreak` | two-space GFM hard break (soft newline with `collapseHardBreaks`) |
| `mention` | `@DisplayName` text |
| `emoji` | the Unicode glyph (or its shortname) |
| `date` | ISO `YYYY-MM-DD` |
| `status` / `placeholder` | bracketed text label, e.g. `\[Blocked\]` |
| `inlineCard` / `blockCard` / `embedCard` | autolink `<url>` or `[text](url)` |

**Confluence macros and extensions**

| Macro / extension | Rendered as |
| --- | --- |
| `toc` | a nested bullet list of heading links, with anchors injected on the referenced headings |
| `anchor` | an `<a id="…"></a>` fragment |
| `pagetree` / `children` | an indented page list via `pageTreeResolver`, else a `{{pagetree}}` / `{{children}}` token |
| `viewpdf` / *view file* | `[PDF: name](attachment:…)` once the title resolves against a `ConfluenceRenderContext` (destination via `attachmentResolver`), else the `\[PDF: name\]` label token |
| `iframe` | a labelled link `[Embedded content](url)` |
| `chart` | a `\[Chart: title\]` label token |
| custom extension | your `ExtensionRenderer` output, else an `\[Extension: type/key\]` placeholder + a `WARNING` diagnostic |
| unrecognized node | governed by [`UnknownNodePolicy`](#controlling-unknown-nodes) |

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Output contains `[Unsupported: <type>]` | The document uses a node type this version does not model. This is `UnknownNodePolicy.PLACEHOLDER` (the default). Choose another [policy](#controlling-unknown-nodes) — `SKIP`, `PRESERVE_RAW`, or `FAIL` — to change the behaviour. |
| A link points at `media:…` or `attachment:…` | No `mediaResolver` (for media nodes) or `attachmentResolver` (for an attachment macro already resolved against the context) was supplied, so the inert placeholder was emitted. Provide the matching [resolver](#resolving-media-and-attachments-to-real-urls). |
| A table rendered as raw HTML `<table>` | The table uses colspan/rowspan, a number column, block-level cell content, or a non-canonical header — none expressible in GFM pipe syntax, so it falls back to HTML by design. See [Tables](#tables-visual-marks-and-other-formatting-toggles). |
| Coloured/highlighted text lost its styling | Visual-only marks have no GFM equivalent and are dropped by default. Enable [`htmlVisualMarks`](#tables-visual-marks-and-other-formatting-toggles) to preserve them as inline HTML. |
| An `[Extension: type/key]` placeholder appears | A macro has no built-in or custom renderer. Register an [`ExtensionRenderer`](#rendering-custom-macros-and-extensions) for it. |
| A `{{pagetree}}` / `{{children}}` token appears | These macros are resolved server-side by Confluence; the ADF carries no page list. Supply a [`pageTreeResolver`](#expanding-page-tree-and-children-macros) — and return an empty list (not `null`) for a page that genuinely has no descendants, which renders as nothing. Fallbacks are listed in `result.unresolved().pageTreeRefs()`. |
| `referencedFileIds()` is missing an attachment | Attachment-*macro* ids only appear when their title resolves against a `ConfluenceRenderContext` passed to `analyze`. See [Planning attachment fetches](#planning-attachment-fetches-before-rendering). |
| Internal page links still point at Confluence | Supply a [`pageLinkResolver`](#rewriting-inter-page-links) to rewrite them to your destinations. |
| `wasLossy()` is `true` but the output looks fine | A `WARNING`/`ERROR` diagnostic was raised (e.g. an unsupported macro or a dropped unknown mark). Inspect `diagnostics()` for the `code`/`message`. By-design placeholders and dropped visual marks do **not** set this flag. |
| Output has trailing whitespace (two spaces) at line ends | That is the GFM hard-break form for a Shift+Enter break. Enable [`collapseHardBreaks`](#tables-visual-marks-and-other-formatting-toggles) to emit clean soft newlines instead. |

## Edge cases and robustness

- **Blank or invalid input** never throws: `convert` returns a `MarkdownResult` with an empty body (plus the configured `documentTitle` heading, if any), `analyze` returns `ContentMetadata.empty()`, and `parse` returns a `ParseResult` whose `validAdfRoot()` is `false`. A `null` `AdfDocument` is treated the same way. (The one hard-failure mode is unrelated: `UnknownNodePolicy.FAIL` throws `IllegalStateException` from `convert`/`toMarkdown` when an *unknown node is rendered* — invalid input short-circuits in the parser before rendering, so it never reaches that path; `parse`/`analyze` never throw under `FAIL` either.)
- **Adversarial input** is bounded: deeply-nested JSON surfaces as an `INVALID_JSON` diagnostic (empty body) rather than overflowing the stack.
- **URL safety**: link, card, media, and macro destinations are scheme-sanitized — `javascript:`, `data:`, and similar non-allow-listed schemes are defused. The one exception is `ExtensionRenderer` output, which is emitted verbatim. See [URL handling and safety](./markdown-conversion.md#url-handling-and-safety).
- **Caller callbacks are sandboxed**: a `RuntimeException` from any resolver or extension renderer is logged and the conversion falls back to default behaviour rather than failing.
