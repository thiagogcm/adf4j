# Guide

Use this guide for integration work: choosing an entry point, reading results, supplying resolvers, rendering macros, reusing the AST, and building documents. For exhaustive tables, see the [reference](./reference.md). For internal design, see the [architecture](./architecture.md).

Conversion examples use the public entry point `dev.nthings.adf4j.AdfToMarkdown`.

## How the conversion works

Every operation uses the same pipeline:

```text
JSON --parse--> AdfDocument --analyze--> ContentMetadata + outline --render--> Markdown
```

Analysis runs before rendering because rendering may need information from the whole document. A table-of-contents macro near the top needs headings later in the document, and heading anchors must be unique before output begins.

| Entry point           | Runs                   | Returns           | Use when                                                                 |
| --------------------- | ---------------------- | ----------------- | ------------------------------------------------------------------------ |
| `AdfJson.parse(json)` | parse                  | `ParseResult`     | You need the typed AST and parse diagnostics.                            |
| `analyze(parsed)`     | analyze                | `ContentMetadata` | You need references, attachments, excerpts, or outline without Markdown. |
| `convert(...)`        | parse, analyze, render | `MarkdownResult`  | You need Markdown plus metadata and diagnostics.                         |
| `toMarkdown(json)`    | parse, analyze, render | `String`          | You only need the Markdown body.                                         |

## Choosing an entry point

Create a converter with `create()` for the defaults, or `with(options)` to bind options:

```java
import dev.nthings.adf4j.AdfToMarkdown;

AdfToMarkdown converter = AdfToMarkdown.create();
String markdown = converter.toMarkdown(adfJson);
```

`AdfToMarkdown` is immutable and thread-safe. An instance is just its bound options over one pipeline shared by the whole process, so creating one per document is cheap. See [Options per document](#options-per-document).

Method details:

- `AdfJson.parse(String)` returns a `ParseResult` with an `AdfDocument` and parse diagnostics. `isValid()` tells whether it produced a document. It never throws on bad input.
- `AdfJson.write(AdfDocument)` returns the document as ADF JSON.
- `analyze(ParseResult|AdfDocument)` returns `ContentMetadata`, or `ContentMetadata.empty()` for an invalid `ParseResult`.
- `convert(String|ParseResult|AdfDocument|ExcerptDefinition)` returns `MarkdownResult`.
- `toMarkdown(String)` is convenience for `convert(json).body()`.

When rendering the same document with different options, parse once:

```java
import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.result.MarkdownResult;

ParseResult parsed = AdfJson.parse(adfJson);
MarkdownResult asOfJanuary = AdfToMarkdown.with(januaryOptions).convert(parsed);
MarkdownResult asOfToday = AdfToMarkdown.with(todayOptions).convert(parsed);
```

Prefer `convert(ParseResult)` over `convert(AdfDocument)` when the input came from `AdfJson.parse(...)`; the `ParseResult` carries parse diagnostics into the final result. Blank input is the one invalid case with no diagnostic: `isValid()` is false, the body is empty, and `wasLossy()` is false.

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

- `pageIds()`: page node IDs a configured `PageLinkResolver` declined. Without a resolver, no lookup happens and nothing is recorded.
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

`MarkdownOptions` is immutable. Start from `defaults()` and chain `withX(...)`, each of which returns a new instance:

```java
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.TableFallback;

MarkdownOptions options = MarkdownOptions.defaults()
    .withHtmlVisualMarks(true)
    .withTableFallback(TableFallback.GFM_EMPTY_HEADER)
    .withCollapseHardBreaks(true);

AdfToMarkdown converter = AdfToMarkdown.with(options);
```

For the full option table, see [Options](./reference.md#options-markdownoptions).

## Resolving media and attachments

Media nodes carry IDs, not URLs. Without a resolver, they render as placeholders such as `media:contentId-42/abc-123`.

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withMediaResolver(media -> {
        if (media.id() == null) {
            return null;
        }
        return "https://cdn.example.com/files/" + media.id();
    });
```

The resolver receives the node as an `AdfMedia`, with accessors such as `id()`, `collection()`, `sourceType()` (the media `type` attribute: `file`, `link`, or `external`), `fileName()`, and `fileMimeType()`. A node with its own `url` attribute, such as external media, never reaches the resolver. `localId()` is editor-local and never identifies a file.

The simplest way to get real attachment links is to supply the page's attachment inventory with each entry's `downloadUrl`. Confluence's `GET /wiki/api/v2/pages/{id}/attachments` returns these fields as `fileId`, `title`, `mediaType`, and `downloadLink`, which is relative to `/wiki`. Attachment macros such as `viewpdf` and `media`/`mediaInline` file nodes then link to the original URL by default, with no resolver:

```java
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.List;

List<AttachmentReference> attachments = List.of(
    new AttachmentReference(
        "file-123", "Q3 Report.pdf", "application/pdf",
        "https://example.atlassian.net/wiki/rest/api/content/42/child/attachment/att9/download"));

MarkdownOptions options = MarkdownOptions.defaults().withAttachments(attachments);
```

An entry is identified by its `fileId` (the media file UUID, `fileId` in the REST API): a blank one is ignored, and the first entry with a given `fileId` wins. Media file nodes match the inventory by `fileId`. Macros match by the `fileId`, `id`, or `attachmentId` parameter they carry, else by title, and a macro that carries a file id still resolves when the inventory doesn't list it. Both lookups trim and ignore case. The inventory's `mediaType` and `title` also decide whether a file node renders as an image.

Each path has its own rewrite hook when you need other destinations, for example local paths. `AttachmentResolver` overrides the `downloadUrl` for attachment macros, and `MediaResolver` overrides it for media nodes:

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withAttachments(attachments)
    .withAttachmentResolver(ref -> "https://cdn.example.com/files/" + ref.fileId());
```

Without an inventory, a title-based attachment macro cannot resolve and stays an escaped token. Without a `downloadUrl` or an `AttachmentResolver`, a resolved macro's destination stays `attachment:<fileId>`.

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

The resolver is used for inline page links, page smart cards, and page-tree entries. The IDs are the same ones exposed by `ContentMetadata.pageIds()`.

## Expanding page-tree and children macros

`pagetree` and `children` macros depend on the Confluence page hierarchy, which is not embedded in ADF. Without a resolver, they render as tokens such as `{{pagetree}}`, `{{pagetree:<root>}}`, `{{children}}`, or `{{children:<depth>}}`, and are recorded in `result.unresolved().pageTreeRefs()`.

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

Each `PageTreeEntry` has a 0-based `depth`, a visible `title`, and a `pageNodeId`. The shallowest entry shifts to column 0. If the entry ID does not resolve through `PageLinkResolver`, the title renders as plain text.

## Expanding excerpt-include macros

An `excerpt` macro marks reusable content on a source page. An `excerpt-include` macro embeds that content elsewhere, but ADF only stores the source title and optional excerpt name.

Index source excerpts, then answer includes from that index:

```java
import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import java.util.HashMap;
import java.util.Map;

Map<String, String> excerptIndex = new HashMap<>();
for (ExcerptDefinition excerpt : converter.analyze(AdfJson.parse(sourceAdf)).excerpts()) {
    String md = converter.convert(excerpt).body();
    excerptIndex.put(sourceTitle + " / " + excerpt.name(), md);
}

MarkdownOptions options = MarkdownOptions.defaults()
    .withExcerptResolver(ref ->
        excerptIndex.get(ref.page() + " / " + ref.excerptName()));
```

`convert(ExcerptDefinition)` renders the excerpt's own content and ignores any `documentTitle`. Returned Markdown is inserted verbatim. Returning `null` keeps the placeholder and records the include in `result.unresolved().excerptRefs()`.

## Expanding the attachments macro

The `attachments` macro lists the page's attachment inventory. Supply that inventory with `withAttachments`:

```java
MarkdownOptions options = MarkdownOptions.defaults()
    .withAttachments(pageAttachments)
    .withAttachmentResolver(ref -> "files/" + ref.title());
```

An empty list is authoritative and renders nothing. A `null` inventory, the default, means it is unknown: the macro keeps the extension placeholder and emits a lossy diagnostic. Macro display parameters such as `patterns`, `labels`, and sort order are not interpreted.

## Rendering custom macros and extensions

Register `ExtensionRenderer`s to render unsupported macros or override built-ins. Renderers run in order before the built-in Confluence macro handlers. The first non-null result wins.

```java
import dev.nthings.adf4j.options.ExtensionRenderer;
import java.util.List;

ExtensionRenderer infoPanel = extension -> {
    if (!"com.atlassian.confluence.macro.core".equals(extension.extensionType())
            || !"info".equals(extension.extensionKey())) {
        return null;
    }
    return "> :information_source: " + extension.macroParams().value("body");
};

MarkdownOptions options = MarkdownOptions.defaults()
    .withExtensionRenderers(List.of(infoPanel));
```

The renderer receives the node as an `AdfExtension`, which exposes `extensionType()`, `extensionKey()`, `text()`, the flattened `macroParams()` with `value(name)`, and the raw `parameters()` envelope, the only place some extensions keep their data. An empty string suppresses the extension. For a bodied extension the result replaces the header line only, and the body blocks still render. Returned Markdown is not sanitized.

## Controlling unknown nodes

Unknown ADF node types are preserved in the AST as `Unknown*` records. `UnknownNodePolicy` controls rendering:

```java
import dev.nthings.adf4j.options.UnknownNodePolicy;

MarkdownOptions options = MarkdownOptions.defaults()
    .withUnknownNodePolicy(UnknownNodePolicy.PRESERVE_RAW);
```

| Policy         | Effect                                                                                                          |
| -------------- | --------------------------------------------------------------------------------------------------------------- |
| `PLACEHOLDER`  | Emit `[Unsupported: <type>]`, or `[Unsupported inline: <type>]` for an inline node, and record a `WARNING`.     |
| `SKIP`         | Drop the node and record a `WARNING`.                                                                           |
| `PRESERVE_RAW` | Emit the node's original JSON, as a fenced block or an inline code span, and record an `INFO` diagnostic.       |
| `FAIL`         | Throw `UnknownNodeException`, whose `nodeType()` names the node, when an unknown node is rendered.              |

Unknown marks on rendered text, media, paragraphs, and headings are dropped with a `WARNING`. `FAIL` is the only hard-failure rendering policy.

## Planning fetches with analyze-first

Use `analyze(...)` to discover required resources before rendering:

```java
import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.result.ParseResult;
import java.util.Map;
import java.util.Set;

ParseResult parsed = AdfJson.parse(adfJson);
ContentMetadata metadata = AdfToMarkdown.create().analyze(parsed);

Set<String> fileIds = metadata.referencedFileIds();
Map<String, String> urls = myStorage.fetchAll(fileIds);

MarkdownOptions options = MarkdownOptions.defaults()
    .withMediaResolver(media -> urls.get(media.id()));

String markdown = AdfToMarkdown.with(options).convert(parsed).body();
```

Media nodes always contribute to `referencedFileIds()`. A `viewpdf` macro contributes only when it carries a file id or its title resolves against the supplied attachment inventory, and an `attachments` macro contributes every inventory entry. Analyze with the same inventory you plan to render with, since `analyze` reads it from the converter's options. A reference to an inventoried file also carries the entry's `downloadUrl`.

`ContentMetadata` also exposes `pageIds()`, `externalUrls()`, `mentionRefs()`, `attachmentRefs()`, `pageTreeRefs()`, `excerptRefs()`, `excerpts()`, and `outline()`. It reports what renders: a subtree the renderer drops contributes nothing, and a macro with a body adds no reference of its own, because it never expands.

## Working with the parsed AST

The AST under `dev.nthings.adf4j.ast` is a sealed hierarchy of immutable records. Parse once and inspect the document directly:

```java
import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.ast.*;
import dev.nthings.adf4j.result.ParseResult;

ParseResult parsed = AdfJson.parse(adfJson);
if (parsed.isValid()) {
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

A record keeps its node's complete `attrs`, `content`, and `marks`, so `AdfJson.write(doc)` writes a parsed document back without loss. Typed accessors are lenient views that hide off-schema children: `MediaSingle.media()` and `captions()`, `MediaGroup.items()`, and `TaskList.items()`, which returns `List<TaskListContent>` because it holds task items and nested task lists. `AdfMedia`, `AdfCard`, and `AdfExtension` carry the accessors shared across node types, and `AdfUnknown` covers `UnknownBlock`, `UnknownInline`, and `UnknownMark`, with `type()` and `rawJson()`.

## Building documents

`dev.nthings.adf4j.ast.Adf` builds documents in code. Its factories return the same immutable records the parser produces, so a built document works with `AdfJson.write`, `AdfToMarkdown`, and everything else that takes an `AdfDocument`.

```java
import static dev.nthings.adf4j.ast.Adf.*;

import dev.nthings.adf4j.AdfJson;
import java.time.LocalDate;

var document =
    doc(
        heading(1, "Release 2.0"),
        paragraph(
            text("Shipped on "),
            date(LocalDate.of(2026, 11, 1)),
            text(" by "),
            mention("5b10a2844c20165700ede21g", "@Ana"),
            text(". See the "),
            text("notes", link("https://example.com/notes")),
            text(".")),
        panel(PanelType.WARNING, paragraph(text("Restart required", strong(), textColor("#bf2600")))),
        orderedList(listItem("Stop the service"), listItem("Upgrade")),
        taskList(taskItem(TaskState.TODO, "Update the runbook")),
        codeBlock("bash", "./upgrade.sh"),
        table(
                tableRow(tableHeader("Area"), tableHeader("State")),
                tableRow(tableCell("Search"), tableCell(paragraph(status("DONE", StatusColor.GREEN)))))
            .withLayout(TableLayout.WIDE),
        mediaSingle(externalMedia("https://example.com/chart.png").withAlt("Chart"), caption("Latency")));

String json = AdfJson.write(document);
```

The static import also brings in the enums for closed attribute sets, such as `PanelType`, `TaskState`, and `StatusColor`. A method with the same name as a factory hides the import, including a record component's accessor, so inside `record Report(String status)`, qualify the call as `Adf.status(...)`.

The signatures carry the schema's rules, so these don't compile:

```java
listItem(heading(1, "No"));                     // a listItem can't hold a heading
panel(PanelType.INFO);                          // a panel needs content
layoutSection(layoutColumn(100, paragraph("x"))); // a section has two or three columns
text("x", alignment(TextAlign.CENTER));         // alignment isn't a text mark
paragraph("x").withMarks(strong());             // strong isn't a paragraph mark
```

Types can't express everything, and these rules aren't checked:

- Numeric ranges, such as heading levels 1 to 6.
- `code` combining only with `link` and `annotation`.
- Block marks in context. Alignment and indentation on a paragraph or heading are accepted only in some containers, not in panels, list items, blockquotes, expands, or extensions, and the two can't combine. `small()` is refused only inside a blockquote.

adf4j doesn't include a schema validator. To check generated documents, run `AdfJson.write` output through a JSON Schema validator with the [ADF schema](spec/adf-schema.json). Atlassian notes that a schema-valid node may still not be accepted by a given product: Jira, for example, may refuse layouts.

Text carries its marks: `text("bold", strong())`, `text("docs", link(url))`, `text("x", strong(), em())`. A paragraph or heading takes block marks with `withMarks`, as in `paragraph("Centered").withMarks(alignment(TextAlign.CENTER))`.

Optional attributes are `withX` methods on the record, which return a copy:

| Record | Methods |
| --- | --- |
| `Table` | `withLayout`, `withWidth`, `withNumberColumn` |
| `TableCell` | `withColspan`, `withRowspan`, `withColwidth`, `withBackground` |
| `OrderedList` | `withOrder` |
| `MediaSingle` | `withLayout`, `withWidth`, `withPixelWidth`, `withLink` |
| `Media` | `withAlt`, `withSize`, `withMarks` |
| `Paragraph`, `Heading` | `withMarks` |
| `TaskList`, `TaskItem`, `BlockTaskItem`, `DecisionList`, `DecisionItem` | `withLocalId` |

For anything else, a record's constructor takes its `attrs`, and `Attributes.with(key, value)` adds to them.

Every container also takes a `List`, for content built from data. A container the schema requires to be non-empty throws `IllegalArgumentException` naming the node when the list is empty, so an empty query result fails where you can see it rather than as a rejected document:

```java
var rows = people.stream().map(p -> tableRow(tableCell(p.name()), tableCell(p.role()))).toList();
var roster = table(rows);
```

Empty text is no node at all, wherever it appears, because the schema forbids it and Jira rejects it. `paragraph(text("Phone: "), text(phone))` is safe when `phone` is empty, and so is a `status` with no text.

To add to an existing document, use `append`, which works on a parsed document and keeps its unknown nodes and existing ids:

```java
AdfDocument updated = AdfJson.parse(adfJson).document().append(paragraph("Reviewed"));
```

The schema requires a `localId` on tasks, decisions, and sync blocks. `doc` and `append` fill these in from each node, so rebuilding the same content writes the same JSON:

- A task or decision's id comes from its content, including formatting. Ticking it, or changing other items, leaves its id alone. Editing its text or formatting gives it a new one.
- Identical items are told apart by their order.
- A task or decision list's id comes from its order among lists, so inserting a list before it changes it.
- A value from `withLocalId` is kept, so use it when an id must outlive an edit. Generated ids avoid every other id on a known node, but a node that already has an id keeps it, so adding the same built node twice repeats its id.
- A node has no generated id until it's in a `doc`.

`extension` writes `parameters` with their keys sorted, so a `Map.of` writes the same JSON on every run. A Confluence macro stores its parameters in an envelope, which `macroParameters` writes for you:

```java
var toc =
    extension(
        "com.atlassian.confluence.macro.core", "toc", macroParameters(Map.of("maxLevel", "3")));
```

## Options per document

`create()` and `with(options)` bind options to a converter, and there are no per-call option overloads. A converter is just its options over one process-wide pipeline, so bind one per document:

```java
for (Page page : pages) {
    MarkdownOptions perPage = MarkdownOptions.defaults()
        .withAttachments(page.attachments())
        .withMediaResolver(media -> page.mediaUrl(media.id()));

    String md = AdfToMarkdown.with(perPage).toMarkdown(page.adfJson());
}
```

This is the preferred pattern for per-page attachment tables, media base URLs, and page-link maps. Combine it with `AdfJson.parse()` plus `AdfToMarkdown.with(options).convert(parsed)` when you need to render the same document under several option sets.
