# adf4j feature request — support for currently-unsupported Confluence extensions

**Context.** We build SpaceGit, a backup tool that mirrors entire Confluence Cloud spaces into Git repositories of Markdown, replaying each page's full version history as commits. adf4j is our ADF→Markdown engine; every page body, at every historical version, goes through `parse(json)` + `convert(ParseResult, options)`, often rendered several times per version (we re-render documents under different resolver states to reconstruct point-in-time views — same usage pattern as our previous feedback round). The output is an archive people read directly, so every macro that falls back to a literal placeholder token is a visible hole in otherwise clean documents.

**Evidence.** Syncing a real, public space — the Open Finance Brasil developer portal, `https://openfinancebrasil.atlassian.net/wiki`, space `OF` (~2,500 pages, anonymous access, so this is fully reproducible on your side) — produced placeholder fallbacks for four extension types. Occurrence counts below are render events from roughly the first third of one full-history sync, inflated by our render-many-times pattern, but the ranking is representative:

| Extension type | Renders hit | What it is |
| --- | --- | --- |
| `com.atlassian.confluence.macro.core/excerpt-include` | 482 | Embeds the `excerpt`-marked region of another page |
| `com.atlassian.chart/chart:default` | 340 | Chart rendered from table data in the macro body |
| `com.atlassian.confluence.migration/inline-media-image` | 4 | Legacy inline image (editor-migration artifact) |
| `com.atlassian.confluence.macro.core/attachments` | 2 | Table of the page's attachments |

The first two matter most: on this portal, `excerpt-include` is how shared changelog/notice fragments are composed into many pages, and chart macros carry real data that currently vanishes into a `{{chart}}` token.

## Requests

**1. `excerpt-include` (and `excerpt`) via a resolver hook.**

This is compositional content, so — like `pageLinkResolver` and `pageTreeResolver` — the library can't know the answer but the consumer can. Proposal: an `ExcerptResolver` hook, `(pageRef, excerptName) → ADF fragment or Markdown`, where a non-null result is rendered inline and null keeps today's placeholder. Two supporting asks, valuable even without the resolver:

- Surface the referenced page in `ContentMetadata.pageRefs()` (and `unresolved()` when declined). Today an `excerpt-include` is invisible to dependency metadata, so consumers tracking cross-page dependencies (we re-render pages when content they embed changes) miss a real edge.
- Recognize the `excerpt` macro itself on source pages: render its body content transparently (it is just a marked region), and ideally expose the marked region in metadata so a resolver implementation can extract it from the source page's ADF.

**2. `chart`: render the data instead of dropping it.**

The chart macro's rich body contains the table the chart is drawn from. Markdown can't draw the chart, but the data is the content worth archiving. Proposal: render the macro's nested table content as a normal table (optionally prefixed with an italic caption such as _Chart: <title>_), reserving the placeholder for a chart macro with no recoverable body. Losing the picture is acceptable; losing the numbers is not.

**3. `attachments`: expand from the attachment context already supplied.**

Consumers already hand adf4j the page's attachment inventory via `ConfluenceRenderContext.withAttachmentReferences(...)` plus an `AttachmentResolver`. That is everything needed to render this macro as a list of links to the local files — exactly analogous to how `pagetree` expands through `PageTreeResolver`. An empty inventory can render as nothing (consistent with the empty-is-authoritative semantics adopted for tree macros); only a missing context keeps the placeholder.

**4. `inline-media-image`: treat as a media node.**

This migration-era macro carries an attachment/media reference in its parameters. Proposal: normalize it into the standard media rendering path (`MediaResolver`/`AttachmentResolver`), so it resolves to a local image link wherever an equivalent `media` node would. Low volume, but it currently degrades pages that are otherwise perfectly convertible.

**5. Generic escape hatch: a consumer-supplied extension renderer.**

The long tail of `extensionType/key` pairs is unbounded, and each specific request above is an instance of the same need. Proposal: a registry-style hook, e.g. `MarkdownOptions.extensionRenderer((type, key, node) → Markdown or null)`, consulted before the placeholder fallback. Null falls through to today's behavior, so it is purely additive — and it would let consumers unblock themselves on site-specific macros without waiting on upstream releases.

**6. Logging: dedupe or demote the per-occurrence WARN.**

`internal.render.MacroRenderer` logs one WARN per placeholder render. Combined with render-many-times usage this produced 828 WARN lines in ~30 minutes for what is really four distinct facts, drowning the log signal consumers actually watch (we ship every unsupported-macro fact to per-page metadata from `result.diagnostics()` already, so nothing is lost without the log). Proposal: log once per extension type per `AdfToMarkdown` instance (or demote to DEBUG) and let the diagnostics carry the per-document detail — they already do.

## Resolution (2026-06)

All six requests are implemented. Before designing, we pulled real instances of each macro from the `OF` space (current and historical versions, anonymous REST) — two of the observed wire shapes differ from the request's mental model, and the deviations below follow from that evidence. Reference: [conversion behaviours](docs/markdown-conversion.md#lossy-and-by-design-behaviours), [usage recipes](docs/usage-guide.md#expanding-excerpt-include-macros).

**1. `excerpt-include` / `excerpt` — shipped, with one deviation.** `MarkdownOptions.withExcerptResolver(ExcerptResolver)` expands the macro: the resolver receives an `ExcerptIncludeReference` and returns Markdown emitted in place (empty string suppresses; `null`/throw declines to an `[Excerpt include: <page> / <name>]` placeholder and records the reference in `MarkdownResult.unresolved().excerptRefs()`). Both supporting asks landed: every occurrence is surfaced statically in `ContentMetadata.excerptRefs()`, and source pages expose their marked regions as `ContentMetadata.excerpts()` (name + ADF blocks, ready to render via `convert(new AdfDocument(1, excerpt.content()))` — the exact resolver-index recipe is in the usage guide). The deviation: the macro references its source page **by title, not page id** (confirmed in the wild — `macroParams[""]` carries the title), so the dependency edge lives in the new `excerptRefs()` rather than `pageRefs()`, which is defined over page node ids. Your dependency tracker should treat `excerptRefs()` as a second edge kind keyed by title.

**2. `chart` — shipped, and the evidence reshaped it.** The legacy bodied `chart` macro renders exactly as proposed: an italic `*Chart: <title>*` caption followed by the body's table. But `com.atlassian.chart/chart:default` — your 340-hit case — turned out to be **bodyless**: its data is a separate table in the same document, referenced by a `dataConsumer` source id. That table renders at its own position, so the chart node now contributes a caption only, never a placeholder. (Your space's one real chart has a dangling source id in every historical version; Confluence's own export renders it as nothing, so the caption is strictly more informative than both the placeholder and Confluence itself.) The placeholder remains only for a legacy bodyless `chart:default`, where the document genuinely holds no data.

**3. `attachments` — shipped as proposed.** With an inventory supplied via `ConfluenceRenderContext.withAttachmentReferences(...)`, the macro expands to a bullet list of links, destinations routed through `AttachmentResolver` (else `attachment:<fileId>`). Supplying the inventory is authoritative even when empty — `withAttachmentReferences(List.of())` renders the macro as nothing — and only a context that never supplied one keeps the placeholder (`ConfluenceRenderContext.attachmentsSupplied()` carries the distinction). The expansion also feeds `referencedFileIds()`. Display parameters (`patterns`, `labels`, sort order) are not interpreted; the full inventory is listed.

**4. `inline-media-image` — shipped as proposed.** Normalized into the standard media path: the macro's `id`/`collection` (which sit directly under `parameters`, not `macroParams`) become media attributes resolved by your existing `MediaResolver`, rendering an image link wherever an equivalent `media` node would, with the same `media:<collection>/<id>` placeholder fallback. The file id is counted in `referencedFileIds()` like any media node's.

**5. Generic extension renderer — already existed; widened.** `MarkdownOptions.withExtensionRenderers(...)` shipped in the previous API round: renderers are consulted in order, before every built-in, on all four extension node forms, with `null` falling through. New in this round: `ExtensionContext.rawParameters()` exposes the node's full raw parameter envelope, because the long tail (modern charts, migration macros) keeps its data outside `macroParams` — without it the escape hatch couldn't see what it needed.

**6. Logging — shipped.** The placeholder WARN now fires once per extension type/key per `AdfToMarkdown` instance; repeats log at DEBUG. Your 828-line scenario becomes four WARN lines, and `result.diagnostics()` remains the per-document source of truth. Note one accounting shift: `excerpt-include` and a supplied-context `attachments` are now *supported* macros, so they no longer appear in `UNSUPPORTED_MACRO` diagnostics — declined excerpt lookups report through `unresolved().excerptRefs()` instead.

**Compatibility.** The record constructors of `ContentMetadata`, `UnresolvedReferences`, `ConfluenceRenderContext`, `ExtensionContext`, and the four extension AST nodes gained components (source-breaking for direct construction; builder/wither call sites are unaffected).
