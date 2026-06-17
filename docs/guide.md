# Guide

Use this guide for integration work: choosing an entry point, reading results, supplying resolvers, rendering macros, and reusing the AST. For exhaustive tables, see the [reference](./reference.md). For internal design, see the [architecture](./architecture.md).

All examples use `dev.nthings:adf4j:1.0.0` and the public entry point `dev.nthings.adf4j.AdfToMarkdown`.

## How the conversion works

Every operation uses the same pipeline:

```text
JSON --parse--> AdfDocument --analyze--> ContentMetadata + outline --render--> Markdown
```

Analysis runs before rendering because rendering may need information from the whole document. A table-of-contents macro near the top needs headings later in the document, and heading anchors must be unique before output begins.

| Entry point        | Runs                   | Returns           | Use when                                                                 |
| ------------------ | ---------------------- | ----------------- | ------------------------------------------------------------------------ |
| `parse(json)`      | parse                  | `ParseResult`     | You need the typed AST and parse diagnostics.                            |
| `analyze(...)`     | parse, analyze         | `ContentMetadata` | You need references, attachments, excerpts, or outline without Markdown. |
| `convert(...)`     | parse, analyze, render | `MarkdownResult`  | You need Markdown plus metadata and diagnostics.                         |
| `toMarkdown(json)` | parse, analyze, render | `String`          | You only need the Markdown body.                                         |

## Choosing an entry point

Create one converter and reuse it:

```java
import dev.nthings.adf4j.AdfToMarkdown;

AdfToMarkdown converter = AdfToMarkdown.create();
String markdown = converter.toMarkdown(adfJson);
```

`AdfToMarkdown` is immutable and thread-safe. Its pipeline is created once per instance. Use per-call options for document-specific resolver state.

Method details:

- `parse(String)` returns a `ParseResult` with an `AdfDocument`, parse diagnostics, and `validAdfRoot`.
- `analyze(String|AdfDocument)` returns `ContentMetadata`.
- `convert(String|ParseResult|AdfDocument)` returns `MarkdownResult`.
- `toMarkdown(String)` is convenience for `convert(json).body()`.

When rendering the same document with different options, parse once:

```java
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.result.MarkdownResult;

ParseResult parsed = converter.parse(adfJson);
MarkdownResult asOfJanuary = converter.convert(parsed, januaryOptions);
MarkdownResult asOfToday = converter.convert(parsed, todayOptions);
```

Prefer `convert(ParseResult, options)` over `convert(AdfDocument, options)` when the input came from `parse(...)`; the `ParseResult` carries parse diagnostics into the final result.

## Results, diagnostics, and lossiness

`convert(...)` returns:

```java
MarkdownResult result = converter.convert(adfJson);

result.body();         // Markdown body
result.metadata();     // references, attachments, excerpts, outline
result.diagnostics();  // parse, analyze, and render diagnostics
result.unresolved();   // resolver lookups this render declined
```

Diagnostics have `INFO`, `WARNING`, or `ERROR` severity:

- `INFO`: non-lossy note, such as an unknown node preserved as raw JSON.
- `WARNING`: content was altered or dropped.
- `ERROR`: conversion failed structurally or produced an empty body.

Use `wasLossy()` to gate quality:

```java
if (result.wasLossy()) {
    result.diagnostics().stream()
        .filter(d -> d.severity() != Diagnostic.Severity.INFO)
        .forEach(d -> log.warn("Conversion issue [{}]: {}", d.code(), d.message()));
}
```

`wasLossy()` is true only for `WARNING` or `ERROR`. It ignores expected option-driven output, such as `media:` placeholders when no resolver is configured, dropped visual marks when `htmlVisualMarks` is off, and table HTML fallback.

`result.unresolved()` reports declined resolver lookups for this render:

- `pageIds()`: page node IDs that had no page-link answer.
- `pageTreeRefs()`: `pagetree` or `children` macros left as tokens.
- `excerptRefs()`: `excerpt-include` macros left as placeholders.

## The resolver model

adf4j does no I/O. ADF points to media, attachments, pages, and server-side macro output by ID or title. Your application supplies those environment-specific answers through `MarkdownOptions`.

| Hook                 | Used for                                   | Returns               |
| -------------------- | ------------------------------------------ | --------------------- |
| `MediaResolver`      | File and attachment media nodes            | URL `String`          |
| `AttachmentResolver` | Resolved Confluence attachment macros      | URL `String`          |
| `PageLinkResolver`   | Page links, smart cards, page-tree entries | URL `String`          |
| `PageTreeResolver`   | `pagetree` and `children` macros           | `List<PageTreeEntry>` |
| `ExcerptResolver`    | `excerpt-include` macros                   | Markdown `String`     |
| `ExtensionRenderer`  | Custom or overridden extensions            | Markdown `String`     |

Decline a lookup by returning `null`. For URL-returning hooks, blank strings also decline. A non-null answer is authoritative, including empty answers:

- An empty `List` from `PageTreeResolver` means the page has no descendants.
- An empty `String` from `ExcerptResolver` suppresses that include.
- An empty attachment inventory means the page has no attachments.

Resolver exceptions are caught, logged, and treated as declined lookups.

`ExtensionRenderer` and `ExcerptResolver` output is inserted verbatim. Escape untrusted content before returning it. Other resolver URLs are scheme-sanitized by adf4j.

## Configuring a conversion

`MarkdownOptions` is immutable. Build it with `builder()`, `defaults()` plus `withX(...)`, or `toBuilder()`:

```java
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.TableFallback;

MarkdownOptions options = MarkdownOptions.builder()
    .htmlVisualMarks(true)
    .tableFallback(TableFallback.GFM_EMPTY_HEADER)
    .collapseHardBreaks(true)
    .build();

MarkdownOptions same = MarkdownOptions.defaults()
    .withHtmlVisualMarks(true)
    .withCollapseHardBreaks(true);

AdfToMarkdown converter = AdfToMarkdown.with(options);
```

For the full option table, see [Options](./reference.md#options-markdownoptions).

## Resolving media and attachments

Media nodes carry IDs, not URLs. Without a resolver, they render as placeholders such as `media:contentId-42/abc-123`.

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withMediaResolver(attrs -> {
        if (attrs.id() == null) {
            return null;
        }
        return "https://cdn.example.com/files/" + attrs.id();
    });
```

Confluence attachment macros such as `viewpdf` first need a page attachment inventory. The `ConfluenceRenderContext` resolves the macro title to a file ID, then `AttachmentResolver` turns the file ID into a URL.

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

Without the context, a title-based attachment macro cannot resolve. Without the `AttachmentResolver`, the destination stays `attachment:<fileId>`.

## Rewriting inter-page links

Use `PageLinkResolver` to rewrite Confluence page node IDs to your output URLs.

```java
import java.util.Map;

Map<String, String> pageUrls = Map.of(
    "98765", "/docs/onboarding.html",
    "98766", "/docs/security.html");

MarkdownOptions options = MarkdownOptions.defaults()
    .withPageLinkResolver(pageNodeId -> pageUrls.get(pageNodeId));
```

The resolver is used for inline page links, page smart cards, and page-tree entries. The IDs are the same ones exposed by `ContentMetadata.pageRefs()`.

## Expanding page-tree and children macros

`pagetree` and `children` macros depend on the Confluence page hierarchy, which is not embedded in ADF. Without a resolver, they render as `{{pagetree}}` or `{{children}}` tokens and are recorded in `result.unresolved().pageTreeRefs()`.

```java
import dev.nthings.adf4j.metadata.PageTreeMacro;
import dev.nthings.adf4j.options.PageTreeEntry;

MarkdownOptions options = MarkdownOptions.defaults()
    .withPageLinkResolver(pageUrls::get)
    .withPageTreeResolver(reference -> {
        boolean wholeTree = reference.macro() == PageTreeMacro.PAGETREE || reference.all();
        return myHierarchy
            .descendants(reference.root(), wholeTree, reference.depth().orElse(1)).stream()
            .map(p -> new PageTreeEntry(p.depth(), p.title(), p.id()))
            .toList();
    });
```

Each `PageTreeEntry` has a 0-based `depth`, a visible `title`, and a `pageNodeId`. If the entry ID does not resolve through `PageLinkResolver`, the title renders as plain text.

## Expanding excerpt-include macros

An `excerpt` macro marks reusable content on a source page. An `excerpt-include` macro embeds that content elsewhere, but ADF only stores the source title and optional excerpt name.

Index source excerpts, then answer includes from that index:

```java
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import java.util.HashMap;
import java.util.Map;

Map<String, String> excerptIndex = new HashMap<>();
for (ExcerptDefinition excerpt : converter.analyze(sourceAdf).excerpts()) {
    String md = converter.convert(new AdfDocument(1, excerpt.content())).body();
    excerptIndex.put(sourceTitle + " / " + excerpt.name(), md);
}

MarkdownOptions options = MarkdownOptions.defaults()
    .withExcerptResolver(ref ->
        excerptIndex.get(ref.page() + " / " + ref.excerptName()));
```

Returned Markdown is inserted verbatim. Returning `null` keeps the placeholder and records the include in `result.unresolved().excerptRefs()`.

## Expanding the attachments macro

The `attachments` macro lists the page's attachment inventory. Supply that inventory through `ConfluenceRenderContext`:

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withConfluenceContext(ConfluenceRenderContext.empty()
        .withAttachmentReferences(pageAttachments))
    .withAttachmentResolver(ref -> "files/" + ref.title());
```

An empty inventory is authoritative and renders nothing. A context with no supplied inventory keeps the extension placeholder and emits a lossy diagnostic. Macro display parameters such as `patterns`, `labels`, and sort order are not interpreted.

## Rendering custom macros and extensions

Register `ExtensionRenderer`s to render unsupported macros or override built-ins. Renderers run in order before the built-in Confluence macro handlers. The first non-null result wins.

```java
import dev.nthings.adf4j.options.ExtensionRenderer;
import java.util.List;

ExtensionRenderer infoPanel = ctx -> {
    if (!"com.atlassian.confluence.macro.core".equals(ctx.extensionType())
            || !"info".equals(ctx.extensionKey())) {
        return null;
    }
    return "> :information_source: " + ctx.parameter("body");
};

MarkdownOptions options = MarkdownOptions.defaults()
    .withExtensionRenderers(List.of(infoPanel));
```

`ExtensionContext` exposes `extensionType()`, `extensionKey()`, `text()`, flattened macro `parameters()`, `parameter(name)`, and raw `rawParameters()`. Returned Markdown is not sanitized.

## Controlling unknown nodes

Unknown ADF node types are preserved in the AST as `Unknown*` records. `UnknownNodePolicy` controls rendering:

```java
import dev.nthings.adf4j.options.UnknownNodePolicy;

MarkdownOptions options = MarkdownOptions.defaults()
    .withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW);
```

| Policy         | Effect                                                          |
| -------------- | --------------------------------------------------------------- |
| `PLACEHOLDER`  | Emit `[Unsupported: <type>]`.                                   |
| `SKIP`         | Drop the node and log a diagnostic.                             |
| `PRESERVE_RAW` | Emit the node's original JSON and record an `INFO` diagnostic.  |
| `FAIL`         | Throw `IllegalStateException` when an unknown node is rendered. |

Unknown marks are always dropped with a `WARNING`. `FAIL` is the only hard-failure rendering policy.

## Planning fetches with analyze-first

Use `analyze(...)` to discover required resources before rendering:

```java
import dev.nthings.adf4j.metadata.ContentMetadata;
import java.util.Map;
import java.util.Set;

ContentMetadata metadata = converter.analyze(adfJson);

Set<String> fileIds = metadata.referencedFileIds();
Map<String, String> urls = myStorage.fetchAll(fileIds);

MarkdownOptions options = MarkdownOptions.defaults()
    .withMediaResolver(attrs -> urls.get(attrs.id()));

String markdown = converter.toMarkdown(adfJson, options);
```

Attachment macros contribute to `referencedFileIds()` only when their title resolves against a supplied `ConfluenceRenderContext`. Analyze with the same context you plan to render with.

`ContentMetadata` also exposes `pageRefs()`, `pageTreeRefs()`, `excerptRefs()`, `excerpts()`, `externalRefs()`, `mentionRefs()`, and `outline()`.

## Working with the parsed AST

The AST under `dev.nthings.adf4j.ast` is a sealed hierarchy of immutable records. Parse once and inspect the document directly:

```java
import dev.nthings.adf4j.ast.*;

ParseResult parsed = converter.parse(adfJson);
if (parsed.validAdfRoot()) {
    AdfDocument doc = parsed.document();
    for (AdfBlock block : doc.content()) {
        String summary = switch (block) {
            case Heading h -> "H" + h.level();
            case Paragraph p -> "paragraph";
            case Table t -> "table";
            default -> block.getClass().getSimpleName();
        };
    }
}
```

Keep a `default` arm, or handle the `Unknown*` variants explicitly. New ADF node types can expand the sealed `permits` lists in future releases.

## Per-call options vs. bound options

`create()` and `with(options)` bind options to a converter. Per-call option overloads override those bound options only for that call.

```java
AdfToMarkdown converter = AdfToMarkdown.create();

for (Page page : pages) {
    MarkdownOptions perPage = MarkdownOptions.defaults()
        .withConfluenceContext(ConfluenceRenderContext.empty()
            .withAttachmentReferences(page.attachments()))
        .withMediaResolver(attrs -> page.mediaUrl(attrs.id()));

    String md = converter.toMarkdown(page.adfJson(), perPage);
}
```

This is the preferred pattern for per-page attachment tables, media base URLs, and page-link maps. Combine it with `parse()` plus `convert(parsed, perCallOptions)` when you need to render the same document under several option sets.
