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
