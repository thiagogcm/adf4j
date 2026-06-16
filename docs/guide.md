# adf4j — Guide

This guide teaches the mental model first, then walks through the tasks you'll actually do. Read the [concepts](#how-the-conversion-works) once and the recipes will click. For exhaustive lookup tables — every option, the full ADF→Markdown matrix, diagnostics, exit codes — see the [reference](./reference.md); for internals, see the [architecture](./architecture.md).

All examples assume the converter is on your module path as `dev.nthings:adf4j:1.0.0`. The single entry point is `dev.nthings.adf4j.AdfToMarkdown`.

## How the conversion works

A conversion flows through three phases — **parse → analyze → render** — and each entry point on `AdfToMarkdown` simply chooses how far down that pipeline to run.

```text
JSON ──parse──► AdfDocument ──analyze──► outline + ContentMetadata ──render──► Markdown
                (typed AST)              (headings, references)                (GFM body)
```

The order is not incidental: **analysis runs before rendering and feeds it.** A Table-of-Contents macro near the top of a document points at headings further down, and every heading anchor must be globally unique — so the renderer needs the full heading outline before it emits its first line. Running a complete analysis pass up front lets the renderer be a pure forward walk with no look-ahead or backpatching. A useful side effect: the same outline-and-references pass is exposed on its own through `analyze()`, so you can inspect a document without rendering it.

| Entry point        | Runs                     | Returns           | Use when                                          |
| ------------------ | ------------------------ | ----------------- | ------------------------------------------------- |
| `parse(json)`      | parse                    | `ParseResult`     | You want the typed AST and structural diagnostics |
| `analyze(...)`     | parse → analyze          | `ContentMetadata` | You want references/outline **without** rendering |
| `convert(...)`     | parse → analyze → render | `MarkdownResult`  | You want the body **plus** metadata + diagnostics |
| `toMarkdown(json)` | parse → analyze → render | `String`          | You only want the Markdown body                   |

## Choosing an entry point

`AdfToMarkdown` is immutable and thread-safe: build one instance and reuse it across documents and threads. **Reuse, don't rebuild** — the pipeline compiles once per instance, and per-document variation is expressed with per-call options (below), never a fresh converter.

```java
import dev.nthings.adf4j.AdfToMarkdown;

AdfToMarkdown converter = AdfToMarkdown.create();   // default options; share this
String markdown = converter.toMarkdown(adfJson);
```

The four methods accept different inputs so you can avoid re-parsing:

- **`parse(String)` → `ParseResult`** runs phase 1 only and hands back the typed `AdfDocument` plus structural diagnostics.
- **`analyze(...)` → `ContentMetadata`** runs parse + analyze. It accepts a `String` or an already-parsed `AdfDocument`.
- **`convert(...)` → `MarkdownResult`** runs all three phases. It accepts a `String`, a `ParseResult`, or an `AdfDocument`.
- **`toMarkdown(String)` → `String`** is convenience for `convert(json).body()`; it takes a `String` only.

**Parse once, render many.** `convert(ParseResult, ...)` pays the JSON parse a single time and carries the parse issues into every result's diagnostics — the right shape for rendering one document repeatedly under changing options or resolver state:

```java
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.result.MarkdownResult;

ParseResult parsed = converter.parse(adfJson);
MarkdownResult asOfJanuary = converter.convert(parsed, januaryOptions);
MarkdownResult asOfToday   = converter.convert(parsed, todayOptions);
```

The bare `AdfDocument` overloads (`analyze(doc)`, `convert(doc)`) are for direct AST work, but they carry **no** parse diagnostics — prefer the `ParseResult` overload when you want the parse issues to reach the result.

**Per-call vs. bound options.** `create()`/`with(...)` *bind* options to the converter. Every `convert`/`analyze`/`toMarkdown` overload that takes a `MarkdownOptions` argument **overrides** the bound options for that one call. That is the supported way to handle per-document context from a single reusable converter — see [Per-call options vs. bound options](#per-call-options-vs-bound-options).

## Results, diagnostics, and lossiness

`convert(...)` returns a `MarkdownResult` with four parts:

```java
import dev.nthings.adf4j.result.MarkdownResult;

MarkdownResult result = converter.convert(adfJson);

result.body();         // String — the GFM body
result.metadata();     // ContentMetadata — the same refs/outline analyze() returns
result.diagnostics();  // List<Diagnostic>
result.unresolved();   // UnresolvedReferences — what this render's resolvers declined
```

Each `Diagnostic` carries a `code()`, a human `message()`, an optional `cause()`, and a `severity()` on a three-rung ladder: **`INFO`** is a non-lossy note (e.g. an unknown node preserved as raw JSON under `PRESERVE_RAW`), **`WARNING`** means content was converted but altered or dropped (an unsupported macro placeholdered, an unknown mark dropped), and **`ERROR`** means the conversion aborted or produced an empty body (`INVALID_JSON`, a structural failure). The [diagnostics reference](./reference.md#diagnostics) lists every code and the full table.

**`wasLossy()` is the single question to gate on.** It is `true` iff some diagnostic is `WARNING` or `ERROR` — content unexpectedly dropped or altered, or a structural failure. Gate on it (or on each `severity()`), never on "any diagnostic present", because some diagnostics are informational.

```java
if (result.wasLossy()) {                       // log is your own SLF4J logger
    result.diagnostics().stream()
        .filter(d -> d.severity() != Diagnostic.Severity.INFO)
        .forEach(d -> log.warn("Conversion issue [{}]: {}", d.code(), d.message()));
}
```

Crucially, `wasLossy()` **ignores by-design, options-driven loss**: `media:`/`attachment:` placeholders when you set no resolver, visual-only marks dropped with `htmlVisualMarks` off, and the table HTML fallback are all expected outcomes of your configuration, not surprises. The full ledger of what is lossy versus by-design lives in [the reference's lossy catalogue](./reference.md#lossy-and-by-design-behaviours).

`result.unresolved()` reports, per render, the lookups your active resolvers were asked for and declined — no decorator-wrapping needed. `pageIds()` are page node ids a `PageLinkResolver` returned nothing for; `pageTreeRefs()` are `pagetree`/`children` macros that fell back to their token; `excerptRefs()` are `excerpt-include` macros that fell back to theirs. An empty `unresolved()` means this render left no declined-lookup artifacts in the output.

## The resolver model

adf4j performs **no I/O of its own.** ADF references binaries and pages by *id*, not URL — the bytes and the page hierarchy live in your system. The library stays deterministic and testable by delegating every environment-specific lookup back to you through six functional hooks on `MarkdownOptions`:

| Hook                 | Fired for                                             | Returns               |
| -------------------- | ----------------------------------------------------- | --------------------- |
| `MediaResolver`      | File/attachment media nodes                           | a URL `String`        |
| `AttachmentResolver` | Resolved Confluence attachment macros (`viewpdf`)     | a URL `String`        |
| `PageLinkResolver`   | Inter-page links, page smart-cards, page-tree entries | a URL `String`        |
| `PageTreeResolver`   | `pagetree` / `children` macros                        | `List<PageTreeEntry>` |
| `ExcerptResolver`    | `excerpt-include` macros                              | Markdown `String`     |
| `ExtensionRenderer`  | Any extension/macro, consulted before built-ins       | Markdown `String`     |

Two conventions make them predictable:

**Decline vs. answer.** Returning `null` (or blank, for the `String`-valued hooks) **declines** the lookup: the library keeps its placeholder and records the reference on `result.unresolved()`. Any other return is an **authoritative answer** — including the empty ones. An empty `List` from a `PageTreeResolver` means "this page has no descendants" and renders nothing; an empty `String` from an `ExcerptResolver` suppresses the macro; an empty attachment inventory means "no attachments". Don't return `null` to mean "empty" — they are different answers.

**Callback isolation.** Every hook is wrapped by an internal guard: a `RuntimeException` thrown from your lambda is logged and converted into the hook's decline/fallback value. One buggy resolver cannot abort a whole document.

> **Output-trust caveat.** `ExtensionRenderer` and `ExcerptResolver` outputs are emitted **verbatim and are not sanitized** — every other resolver's URL is scheme-sanitized by the library, but these two are your responsibility. Escape untrusted content you interpolate into them. See [URL handling and safety](./reference.md#url-handling-and-safety).

## Configuring a conversion

Configuration is an immutable `MarkdownOptions` value. There is no public constructor — build one with the fluent `builder()` or with `defaults()` plus `withX(...)` withers (both stay source-compatible as options are added), then bind it with `AdfToMarkdown.with(...)` or pass it per call:

```java
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.TableFallback;

// Builder form:
MarkdownOptions options = MarkdownOptions.builder()
    .htmlVisualMarks(true)
    .tableFallback(TableFallback.GFM_EMPTY_HEADER)
    .collapseHardBreaks(true)
    .build();

// Withers-from-defaults form (equivalent style):
MarkdownOptions same = MarkdownOptions.defaults()
    .withHtmlVisualMarks(true)
    .withCollapseHardBreaks(true);

AdfToMarkdown converter = AdfToMarkdown.with(options);
```

Vary an existing instance with `toBuilder()`. The recipes below show only the options each task needs. For the canonical table of all options, their defaults, and exact effects, see [the options reference](./reference.md#options-markdownoptions).

## Resolving media and attachments

ADF media nodes and Confluence attachment macros reference content by **id, not URL**. Without a resolver they render to inert, greppable placeholder destinations such as `media:contentId-42/abc-123` and `[PDF: guide.pdf](attachment:file-1)`. Supply a resolver to turn them into real links.

**Media** (images and files embedded as media nodes) — a `MediaResolver` receives the node's `MediaAttrs`:

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withMediaResolver(attrs -> {
        // attrs exposes id(), collection(), localId(), fileName(), mimeOrType(), ...
        if (attrs.id() == null) {
            return null;                  // null/blank declines → keeps the media: placeholder
        }
        return "https://cdn.example.com/files/" + attrs.id();
    });
```

**Confluence attachment macros** (e.g. *view file* / `viewpdf`) reference a file by **title**. They resolve in two steps: the title is matched against a `ConfluenceRenderContext` (which the analyze phase also uses to recognize the macro and populate metadata), then your `AttachmentResolver` turns the resolved reference into a URL:

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

Without the context the macro's title cannot resolve and the macro keeps its placeholder; without the `AttachmentResolver` the destination stays `attachment:<fileId>`.

## Rewriting inter-page links

Confluence pages link to one another by internal node id. A `PageLinkResolver`, keyed by page node id, rewrites those links to wherever the pages landed in your output:

```java
import java.util.Map;

Map<String, String> pageUrls = Map.of(
    "98765", "/docs/onboarding.html",
    "98766", "/docs/security.html");

MarkdownOptions options = MarkdownOptions.defaults()
    .withPageLinkResolver(pageNodeId -> pageUrls.get(pageNodeId));  // null → keep original href
```

The resolver fires for inline page links, page smart-cards, **and** page-tree entries. The ids it receives are exactly the page node ids carried by `ContentMetadata.pageRefs()`, so you can discover the full set up front with `analyze(...)`.

## Expanding page-tree and children macros

The `pagetree` and `children` macros list pages Confluence assembles *server-side* from the space hierarchy — the ADF carries only the macro reference, not the page list. Without a resolver they render to a `{{pagetree}}` / `{{children}}` token (this is **not** counted as lossy). Supply a `PageTreeResolver` to expand them, returning depth-tagged entries:

```java
import dev.nthings.adf4j.metadata.PageTreeMacro;
import dev.nthings.adf4j.options.PageTreeEntry;

MarkdownOptions options = MarkdownOptions.defaults()
    .withPageLinkResolver(pageUrls::get)   // entry ids route through this for their links
    .withPageTreeResolver(reference -> {
        // reference.macro() is PAGETREE or CHILDREN; reference.root() is the start page id
        // (null for @self/absent). reference.depth() (OptionalInt) and reference.all() are
        // the standard macro parameters pre-parsed; reference.parameters() is the raw map.
        boolean wholeTree = reference.macro() == PageTreeMacro.PAGETREE || reference.all();
        return myHierarchy
            .descendants(reference.root(), wholeTree, reference.depth().orElse(1)).stream()
            .map(p -> new PageTreeEntry(p.depth(), p.title(), p.id()))
            .toList();
    });
```

Each entry's `depth` (0-based) drives indentation, `title` is the visible label (Markdown-escaped for you), and `pageNodeId` is routed through your `pageLinkResolver` to produce the link — an entry whose id doesn't resolve renders as plain text. A non-null return is authoritative: an **empty list means "no descendants"** and renders as nothing. Returning `null` or throwing declines and leaves the placeholder token; each declined macro is listed in `result.unresolved().pageTreeRefs()`.

## Expanding excerpt-include macros

The `excerpt` macro marks a region of a page; `excerpt-include` embeds that region into *other* pages, composed server-side. The ADF carries only a reference to the source page **by title** (plus an optional named-excerpt `name`). On the source page the `excerpt` body renders transparently; an `excerpt-include` without a resolver emits an `[Excerpt include: <page>]` placeholder.

The two sides meet by design: a source page's conversion exposes its marked regions as `ContentMetadata.excerpts()` (the name plus the region's ADF blocks), and an `ExcerptResolver` answers an include with Markdown.

```java
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import java.util.HashMap;
import java.util.Map;

// 1. While converting each source page, index its excerpts (rendered with the same options).
Map<String, String> excerptIndex = new HashMap<>();          // "title / name" -> markdown
for (ExcerptDefinition excerpt : converter.analyze(sourceAdf).excerpts()) {
    String md = converter.convert(new AdfDocument(1, excerpt.content())).body();
    excerptIndex.put(sourceTitle + " / " + excerpt.name(), md);
}

// 2. When converting including pages, answer from the index.
MarkdownOptions options = MarkdownOptions.defaults()
    .withExcerptResolver(ref ->
        excerptIndex.get(ref.page() + " / " + ref.excerptName()));   // null → placeholder
```

The `ExcerptIncludeReference` carries `page()` (the source title exactly as stored, optionally `SPACEKEY:`-prefixed for a cross-space include), `excerptName()` (`null` for the unnamed excerpt), and the raw `parameters()` map. The returned Markdown is emitted **verbatim** in the macro's place (escape untrusted content yourself); an empty string suppresses the macro. Returning `null` or throwing declines: the placeholder stays and the reference appears in `result.unresolved().excerptRefs()`. Every occurrence — resolved or not — is also surfaced statically in `ContentMetadata.excerptRefs()`, so a sync tool can track the cross-page dependency without rendering.

## Expanding the attachments macro

The `attachments` macro renders the page's attachment table — server-side data the ADF doesn't carry, but exactly what `ConfluenceRenderContext.withAttachmentReferences(...)` already supplies for attachment-macro resolution. With the inventory supplied, the macro expands to a bullet list of links, each destination routed through your `attachmentResolver` (or the `attachment:<fileId>` placeholder without one):

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withConfluenceContext(ConfluenceRenderContext.empty()
        .withAttachmentReferences(pageAttachments))   // even an empty list is authoritative
    .withAttachmentResolver(ref -> "files/" + ref.title());
```

Supplying the inventory is authoritative even when empty: `withAttachmentReferences(List.of())` means "this page has no attachments" and the macro renders nothing. Only a context that *never* supplied an inventory (`ConfluenceRenderContext.empty()`) keeps the `[Extension: …/attachments]` placeholder and its lossy diagnostic. Macro display parameters (`patterns`, `labels`, sort order) are not interpreted — the expansion always lists the full supplied inventory.

## Rendering custom macros and extensions

Macros without a built-in renderer become a labelled placeholder and a `WARNING` diagnostic. To render your own — or override a built-in — register one or more `ExtensionRenderer`s. They are consulted **in order, before** the built-in Confluence macros, and the first to return non-null wins (an empty string is a valid, suppressing answer):

```java
import dev.nthings.adf4j.options.ExtensionRenderer;
import java.util.List;

ExtensionRenderer infoPanel = ctx -> {
    // Match on type AND key — keys are only unique within an extension namespace.
    if (!"com.atlassian.confluence.macro.core".equals(ctx.extensionType())
            || !"info".equals(ctx.extensionKey())) {
        return null;                       // defer to the next renderer / built-in
    }
    return "> :information_source: " + ctx.parameter("body");   // read a named macro param
};

MarkdownOptions options = MarkdownOptions.defaults()
    .withExtensionRenderers(List.of(infoPanel));
```

`ExtensionContext` exposes `extensionType()`, `extensionKey()`, `text()`, the flattened macro `parameters()` (with a `parameter(name)` convenience), and `rawParameters()` — the node's full parameter envelope as `Attributes`, for extensions that keep data outside the conventional `macroParams` (the modern chart app and migration macros do). Two cautions, both from [the resolver model](#the-resolver-model): **output is emitted verbatim, not sanitized** — escape untrusted parameters yourself; and a **thrown `RuntimeException` is caught and logged**, with the engine falling back to its built-in handling.

## Controlling unknown nodes

ADF evolves; a document may carry node types this version doesn't model. The parser never discards them — it keeps each one as an `Unknown*` record with its raw JSON — and `UnknownNodePolicy` decides what the renderer does with it:

```java
import dev.nthings.adf4j.options.UnknownNodePolicy;

MarkdownOptions options = MarkdownOptions.defaults()
    .withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW);
```

| Policy                    | Effect                                                                                                                       |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `PLACEHOLDER` *(default)* | Emit a visible `[Unsupported: <type>]` token                                                                                 |
| `SKIP`                    | Drop the node entirely (still logged as a diagnostic)                                                                        |
| `PRESERVE_RAW`            | Emit the node's original JSON (fenced for blocks, inline code for inlines) — round-trippable, recorded as `INFO` (not lossy) |
| `FAIL`                    | Throw `IllegalStateException` when an unknown node is rendered — for pipelines that must not silently lose content           |

Unknown *marks* (decorations on text) have no standalone form: they are always dropped and reported as a `WARNING`, regardless of policy. `FAIL` is the library's only hard-failure mode — every other path degrades to diagnostics, and blank/invalid input short-circuits in the parser before it can reach `FAIL`.

## Planning fetches with analyze-first

A powerful pattern is **analyze first, fetch, then render**. `analyze(...)` runs parse + analyze without rendering and returns `ContentMetadata`, whose `referencedFileIds()` gives you exactly the binaries this document depends on:

```java
import dev.nthings.adf4j.metadata.ContentMetadata;
import java.util.Map;
import java.util.Set;

ContentMetadata metadata = converter.analyze(adfJson);

Set<String> fileIds = metadata.referencedFileIds();       // fetch only what's needed
Map<String, String> urls = myStorage.fetchAll(fileIds);

MarkdownOptions options = MarkdownOptions.defaults()
    .withMediaResolver(attrs -> urls.get(attrs.id()));

String markdown = converter.toMarkdown(adfJson, options);
```

> **Attachment macros need a context to be counted.** Media-node file ids are always collected, but a Confluence attachment macro (e.g. `viewpdf`) contributes its id to `referencedFileIds()` **only when its title resolves against a `ConfluenceRenderContext`**. If your documents use attachment macros, analyze with the context supplied — `converter.analyze(adfJson, optionsWithContext)` — or those ids are silently absent from the fetch plan.

`ContentMetadata` also exposes the document's outbound `pageRefs()`, `pageTreeRefs()`, `excerptRefs()`, the `excerpts()` it defines, `externalRefs()`, `mentionRefs()`, and a heading `outline()` (each `HeadingReference` carries `level`, `text`, and a unique `anchor`) — handy for a link graph, search index, or navigation sidebar without rendering. The first three together classify a document up front: all empty means it is self-contained and renders fully with no page hierarchy, foreign excerpts, or link resolution.

## Working with the parsed AST

The AST under `dev.nthings.adf4j.ast` is a sealed hierarchy of immutable records: `AdfDocument` holds `AdfBlock`s, blocks hold `AdfInline`s, and text/media carry `AdfMark`s (a separate hierarchy). Parse once and reuse the `AdfDocument` for direct inspection:

```java
import dev.nthings.adf4j.ast.*;

ParseResult parsed = converter.parse(adfJson);
if (parsed.validAdfRoot()) {
    AdfDocument doc = parsed.document();
    for (AdfBlock block : doc.content()) {
        String summary = switch (block) {
            case Heading h   -> "H" + h.level();
            case Paragraph p -> "paragraph";
            case Table t     -> "table";
            default          -> block.getClass().getSimpleName();
        };
    }
}
```

**Always keep a `default` arm** (or explicitly handle the `Unknown*` variants): the sealed `permits` lists grow as ADF evolves, so a `default`-less switch would stop compiling on every upgrade. You can construct an `AdfDocument` yourself — `new AdfDocument(1, blocks)` — which is how the excerpt recipe renders a marked region's blocks.

## Per-call options vs. bound options

A converter has *bound* options from `create()`/`with(...)`. The `convert`, `analyze`, and `toMarkdown` overloads that take a `MarkdownOptions` argument **override** the bound options for that call only — the supported way to handle documents whose context differs (per-page attachment tables, media base URLs, page-link maps) from one reusable converter:

```java
AdfToMarkdown converter = AdfToMarkdown.create();   // build once

for (Page page : pages) {
    MarkdownOptions perPage = MarkdownOptions.defaults()
        .withConfluenceContext(ConfluenceRenderContext.empty()
            .withAttachmentReferences(page.attachments()))
        .withMediaResolver(attrs -> page.mediaUrl(attrs.id()));

    String md = converter.toMarkdown(page.adfJson(), perPage);   // same converter, per-call opts
}
```

This avoids the anti-pattern of constructing a new `AdfToMarkdown` for every document. To also avoid re-parsing the same document under varying options, combine this with `parse()` once and `convert(parsed, perCallOptions)` (see [Choosing an entry point](#choosing-an-entry-point)).

---

For the complete option table, the full ADF→Markdown mapping matrix, diagnostic codes, the lossy/by-design catalogue, URL safety rules, and the CLI reference, see [the reference](./reference.md). For the pipeline internals, AST type model, and design decisions, see [the architecture](./architecture.md).
