# Markdown conversion

`adf4j` converts Atlassian Document Format (ADF) JSON to **GitHub-Flavored Markdown (GFM)**. This page documents the conversion options and the behaviours that are lossy or by design. The entry points are `Adf.toMarkdown(String)` (zero-config) and `AdfToMarkdown` (configurable, immutable, thread-safe — build once and reuse).

## Options (`MarkdownOptions`)

Construct via `MarkdownOptions.defaults()` plus the `withX(...)` withers, or via `MarkdownOptions.builder()`. Both are forward-compatible; avoid the canonical record constructor (its parameter list grows as options are added). Options can be bound to a converter with `AdfToMarkdown.with(options)` or supplied per call via the `convert(json, options)` / `analyze(json, options)` overloads.

| Option | Default | Effect |
| --- | --- | --- |
| `unknownNodePolicy` | `PLACEHOLDER` | How an unknown ADF node is handled: `SKIP` (drop it, logged), `PLACEHOLDER` (emit a `[Unsupported: <type>]` token), `PRESERVE_RAW` (emit the node's raw JSON in a fenced code block), or `FAIL` (throw `IllegalStateException`). |
| `context` | empty | `ConfluenceRenderContext` supplying Confluence attachment references (used to resolve `viewpdf`/attachment macros) and related metadata. |
| `imageSizeAttributes` | `false` | When `true`, emits the non-GFM `{width= height=}` suffix after an image. Off by default because the target is plain GFM. |
| `tableFallback` | `GFM_PROMOTE_FIRST_ROW` | How a header-less table is rendered as GFM. `GFM_PROMOTE_FIRST_ROW` promotes the first row to a header; `GFM_EMPTY_HEADER` keeps all rows as data under a synthesized empty header; `HTML` always emits an HTML `<table>`. Tables GFM pipe syntax cannot express (colspan/rowspan, number column, block cell content, non-canonical header placement) fall back to an HTML `<table>` regardless. |
| `mediaResolver` | `null` | Turns a file media node (which carries ids, not a URL) into a concrete URL/path. Returning `null`/blank keeps the synthetic `media:<collection>/<id>` placeholder. |
| `htmlVisualMarks` | `false` | When `true`, preserves visual-only marks (`textColor`, `backgroundColor`, `border`, `fontSize`, and block `alignment`) as inline `<span style>` / `<div align>` HTML instead of dropping them. |
| `extensionRenderers` | empty | Caller hooks (`ExtensionRenderer`) for rendering custom extensions/macros, consulted in order before the built-in Confluence macros. Their output is trusted and emitted verbatim. |
| `attachmentResolver` | `null` | Turns a resolved Confluence `attachment:` reference into a concrete URL/path. Returning `null`/blank keeps the `attachment:<fileId>` placeholder. |
| `pageLinkResolver` | `null` | Rewrites inter-page links and page smart-cards to a caller-supplied destination, keyed by page node id. Returning `null`/blank keeps the original href. |
| `pageTreeResolver` | `null` | Expands a `pagetree` or `children` macro into an indented Markdown bullet list of its descendant pages (the `PageTreeRequest.macro()` discriminates the two). The ADF holds only the macro reference (Confluence builds the list server-side), so the caller supplies the pages as depth-tagged `PageTreeEntry` values; each entry's page node id is routed through `pageLinkResolver` for its link destination. Returning `null`/empty (or throwing) keeps the `{{pagetree}}` / `{{children}}` placeholder token. |
| `collapseHardBreaks` | `false` | When `true`, renders a hard break (Shift+Enter) as a soft break (a plain newline) instead of the two-trailing-space GFM hard break. |
| `documentTitle` | `null` | When set, prepends the value as a level-1 (`# `) heading above the output, separated by a blank line. Newlines collapse to spaces and CommonMark punctuation is escaped; `null`/blank emits nothing (current behaviour). It is render-only (not reflected in `ContentMetadata`) and is not de-duplicated against a title heading the body may already contain. |

Caller-supplied callbacks (`mediaResolver`, `attachmentResolver`, `pageLinkResolver`, `pageTreeResolver`, `extensionRenderers`) are isolated: a `RuntimeException` thrown from one is logged and the conversion falls back to the default behaviour rather than aborting.

## Lossy and by-design behaviours

- **Visual marks are dropped by default.** Colour, background, border, font size and block alignment have no GFM equivalent and are dropped unless `htmlVisualMarks` is enabled. An unknown `subsup` subtype (anything other than `sub`/`sup`) is left unwrapped rather than guessed.
- **Table HTML fallback.** Tables that pipe syntax cannot represent are emitted as raw HTML `<table>`. Empty rows are dropped and short rows are padded to a rectangle.
- **Synthetic media/attachment placeholders.** Without a resolver, file media and attachment macros render to inert `media:`/`attachment:` placeholder destinations.
- **Unsupported macros/extensions.** Macros without a dedicated renderer become a labelled placeholder and are recorded as a lossy-conversion diagnostic in `MarkdownResult.diagnostics()`.
- **Dynamic page-list macros.** The `children` and `pagetree` macros list pages Confluence renders dynamically from the space hierarchy, which the ADF does not carry. They emit a `{{children}}` / `{{pagetree}}` placeholder token (neither counts as lossy). A `pageTreeResolver` can expand either macro into the actual descendant-page list when the caller can supply the hierarchy.

## URL handling and safety

ADF input is treated as untrusted. Link, card, media and macro **destinations are scheme-sanitized**: a destination whose scheme is not in the safe allow-list (`http`, `https`, `mailto`, `tel`, `ftp`, `ftps`, plus the internal `media`/`attachment` placeholders) — for example `javascript:`, `data:` or `vbscript:` — has its scheme colon percent-encoded so it can no longer execute, and control-character obfuscation (e.g. tabs inside the scheme) is stripped first. The HTML table-fallback renderer additionally sanitizes URLs as defence in depth.

Note that `ExtensionRenderer` output is **not** sanitized — it is emitted verbatim, so a custom renderer that interpolates untrusted parameters is responsible for escaping them.

## Robustness

Parsing is hardened against adversarial input: JSON nesting is capped so a pathologically deep payload surfaces as an `INVALID_JSON` diagnostic (with an empty body) rather than overflowing the stack. Blank or invalid input yields an empty result (`MarkdownResult` with an empty body, or `ContentMetadata.empty()`); a `null` already-parsed `AdfDocument` is treated the same way.
