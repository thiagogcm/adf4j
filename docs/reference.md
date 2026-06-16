# Reference

Lookup tables for the adf4j conversion options, the ADF→Markdown mapping, diagnostics, URL safety, and the CLI. For the mental model and task recipes that put these to work, see the [guide](./guide.md); for internals, the [architecture](./architecture.md).

## Options (MarkdownOptions)

`MarkdownOptions` is an immutable value with no public constructor — build it via `MarkdownOptions.defaults()` plus `withX(...)` withers, `MarkdownOptions.builder()`, or `toBuilder()` on an existing instance. Bind it with `AdfToMarkdown.with(options)`, or pass it to a per-call `convert`/`analyze`/`toMarkdown` overload to override the bound options for that call only. All fourteen options:

| Option                | Default                 | Effect                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| --------------------- | ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `unknownNodePolicy`   | `PLACEHOLDER`           | How an unknown ADF node renders: `PLACEHOLDER` emits a `[Unsupported: <type>]` token; `SKIP` drops it (logged); `PRESERVE_RAW` emits its raw JSON (fenced for blocks, inline code for inlines) round-trippably; `FAIL` throws `IllegalStateException`. Unknown *marks* are always dropped with a `WARNING`, regardless of policy.                                                                                                                                                                        |
| `confluenceContext`   | empty                   | A `ConfluenceRenderContext` supplying the page's attachment inventory. Used to resolve `viewpdf`/*view file* macros by title and to expand the `attachments` macro. Supplying an inventory is authoritative even when empty (an empty list renders the macro as nothing); only a context that never supplied one keeps the macro's placeholder.                                                                                                                                                          |
| `imageSizeAttributes` | `false`                 | When `true`, emits the non-GFM `{width= height=}` suffix after an image. Off because the target is plain GFM.                                                                                                                                                                                                                                                                                                                                                                                            |
| `tableFallback`       | `GFM_PROMOTE_FIRST_ROW` | How a header-less table renders as GFM. `GFM_PROMOTE_FIRST_ROW` promotes the first row to a header; `GFM_EMPTY_HEADER` keeps all rows as data under a synthesized empty header; `HTML` always emits an HTML `<table>`. Tables GFM cannot express (colspan/rowspan, number column, block-level cell content, non-canonical header) fall back to HTML regardless. **Caveat:** the default demotes the only row of a single-data-row table to a heading — use `GFM_EMPTY_HEADER` to keep every row as data. |
| `mediaResolver`       | `null`                  | `MediaAttrs -> String`. Turns a file media node (which carries ids, not a URL) into a concrete URL/path. `null`/blank keeps the `media:<collection>/<id>` placeholder.                                                                                                                                                                                                                                                                                                                                   |
| `htmlVisualMarks`     | `false`                 | When `true`, preserves visual-only marks (`textColor`, `backgroundColor`, `border`, `fontSize`, block `alignment`) as inline `<span style>` / `<div align>` HTML instead of dropping them. **Security caveat:** the colour/size values are emitted into the `style` attribute without CSS sanitization — only HTML-attribute breakout is blocked. See [URL handling and safety](#url-handling-and-safety).                                                                                               |
| `extensionRenderers`  | empty                   | `List<ExtensionRenderer>` (`ExtensionContext -> String`). Consulted in order before the built-in Confluence macros; first non-null wins (empty string suppresses). Output is emitted **verbatim** — not sanitized.                                                                                                                                                                                                                                                                                       |
| `attachmentResolver`  | `null`                  | `AttachmentReference -> String`. Turns a resolved Confluence `attachment:` reference into a URL/path. `null`/blank keeps the `attachment:<fileId>` placeholder.                                                                                                                                                                                                                                                                                                                                          |
| `pageLinkResolver`    | `null`                  | `String pageNodeId -> String`. Rewrites inter-page links, page smart-cards, and page-tree entries by page node id. `null`/blank keeps the original href (or renders a tree entry as plain text).                                                                                                                                                                                                                                                                                                         |
| `pageTreeResolver`    | `null`                  | `PageTreeReference -> List<PageTreeEntry>`. Expands a `pagetree`/`children` macro into an indented bullet list (`PageTreeReference.macro()` discriminates the two); each entry's page node id routes through `pageLinkResolver`. A non-null result is authoritative — an empty list means "no descendants" and renders nothing. `null` or a throw keeps the `{{pagetree}}`/`{{children}}` token.                                                                                                         |
| `excerptResolver`     | `null`                  | `ExcerptIncludeReference -> String`. Expands an `excerpt-include` into caller-supplied Markdown, emitted **verbatim** in place (empty string suppresses). `null` or a throw keeps the `[Excerpt include: <page>]` placeholder and records the reference on `MarkdownResult.unresolved()`.                                                                                                                                                                                                                |
| `collapseHardBreaks`  | `false`                 | When `true`, renders a hard break (Shift+Enter) as a soft break (plain newline) instead of the two-trailing-space GFM hard break.                                                                                                                                                                                                                                                                                                                                                                        |
| `documentTitle`       | `null`                  | When set, prepends the value as a level-1 (`# `) heading above the body. Newlines collapse to spaces and CommonMark punctuation is escaped; `null`/blank emits nothing. Emitted even when the body is empty or fails to parse. Render-only (not in `ContentMetadata`) and not de-duplicated against an existing leading heading.                                                                                                                                                                         |
| `escapeParentheses`   | `false`                 | When `true`, backslash-escapes literal `(` and `)` in rendered text and image alt text. Off because parentheses are inert outside a link destination; link safety comes from escaping `[`/`]`, not parentheses.                                                                                                                                                                                                                                                                                          |

A `RuntimeException` thrown from any resolver or extension renderer is caught, logged, and the engine falls back to its default behaviour rather than aborting the conversion.

## ADF → Markdown mapping

How each ADF construct renders with default options. Anchors, autolinks, and Markdown escaping are applied automatically. This matrix is the canonical mapping; behaviours flagged lossy are catalogued under [Lossy and by-design behaviours](#lossy-and-by-design-behaviours).

### Blocks

| ADF construct                          | Rendered as                                                                                                        |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `heading` (levels 1–6)                 | `#` … `######` (levels > 6 clamped to 6); gains an `<a id>` anchor when a `toc` macro references its level         |
| `paragraph`                            | a text block                                                                                                       |
| `bulletList` / `orderedList`           | `-` / `1.` items (nesting indented; `order`/`start` honoured)                                                      |
| `taskList`                             | `- [x]` (DONE) / `- [ ]` (TODO)                                                                                    |
| `decisionList`                         | `- \[decision:DECIDED\] …` / `- \[decision:UNDECIDED\] …`                                                          |
| `blockquote`                           | `>` quote (blank `>` lines between paragraphs)                                                                     |
| `codeBlock`                            | fenced ` ```lang ` block (fence widened to avoid collisions)                                                       |
| `panel`                                | GFM alert: info/note → `> [!NOTE]`, warning → `> [!WARNING]`, error → `> [!CAUTION]`, success/tip → `> [!TIP]`     |
| `rule`                                 | `---` thematic break                                                                                               |
| `table`                                | native GFM pipe table, or an HTML `<table>` fallback (see [tableFallback](#options-markdownoptions))               |
| `expand` / `nestedExpand`              | `<details><summary>…</summary>…</details>`                                                                         |
| `layoutSection` / `layoutColumn`       | columns flattened into sequential blocks                                                                           |
| `mediaSingle` / `mediaGroup` / `media` | image `![alt](url)` or file link `[label](url)`; a `media:<collection>/<id>` placeholder without a `mediaResolver` |

### Inlines and marks

| ADF construct                                                                    | Rendered as                                                                                                                                                                |
| -------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `text`                                                                           | plain text, Markdown punctuation escaped (literal `(`/`)` left unescaped unless `escapeParentheses`)                                                                       |
| `strong` / `em` / `strike` / `code`                                              | `**bold**` / `*italic*` / `~~strike~~` / `` `code` ``                                                                                                                      |
| `underline` / `subsup`                                                           | HTML `<u>…</u>` / `<sub>…</sub>`, `<sup>…</sup>` (an unknown `subsup` subtype is left unwrapped)                                                                           |
| `link`                                                                           | `[text](url)`, URL scheme-sanitized                                                                                                                                        |
| visual marks (`textColor`, `backgroundColor`, `border`, `fontSize`, `alignment`) | dropped by default; HTML `<span style>` / `<div align>` with `htmlVisualMarks`                                                                                             |
| `hardBreak`                                                                      | two-space GFM hard break (soft newline with `collapseHardBreaks`)                                                                                                          |
| `mention`                                                                        | `@DisplayName` text                                                                                                                                                        |
| `emoji`                                                                          | the Unicode glyph (or its shortname)                                                                                                                                       |
| `date`                                                                           | ISO `YYYY-MM-DD`                                                                                                                                                           |
| `status`                                                                         | a bracketed text label, e.g. `\[Blocked\]`                                                                                                                                 |
| `placeholder`                                                                    | its plain placeholder text (escaped) — no brackets                                                                                                                         |
| `inlineCard` / `blockCard` / `embedCard`                                         | autolink `<url>` or `[text](url)`; a title-only card renders as plain text, a card with neither url nor title as a `\[Card\]` / `\[Inline card\]` / `\[Embed card\]` token |
| `mediaInline`                                                                    | inline image `![alt](url)` or file link via `mediaResolver`, else the `media:` placeholder                                                                                 |

### Confluence macros and extensions

| Macro / extension                       | Rendered as                                                                                                                                                                 |
| --------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `toc`                                   | a nested bullet list of heading links, with anchors injected on the referenced headings                                                                                     |
| `anchor`                                | an `<a id="…"></a>` fragment                                                                                                                                                |
| `pagetree` / `children`                 | an indented page list via `pageTreeResolver`, else a `{{pagetree}}` / `{{children}}` token                                                                                  |
| `excerpt`                               | its body, rendered transparently (the region is also exposed as `ContentMetadata.excerpts()`)                                                                               |
| `excerpt-include`                       | the `excerptResolver`'s Markdown, else an `\[Excerpt include: page\]` token                                                                                                 |
| `attachments`                           | a bullet list of links to the supplied attachment inventory (destinations via `attachmentResolver`), else the `\[Extension: …\]` placeholder when no inventory was supplied |
| `viewpdf` / *view file*                 | `[PDF: name](attachment:…)` once the title resolves against a `ConfluenceRenderContext` (destination via `attachmentResolver`), else a `\[PDF: name\]` token                |
| `iframe`                                | a labelled link `[Embedded content](url)`                                                                                                                                   |
| `chart` (bodied)                        | an italic `*Chart: title*` caption followed by the body's data table                                                                                                        |
| `chart` (modern, `com.atlassian.chart`) | an italic `*Chart: title*` caption — its data table is a separate node that renders at its own position                                                                     |
| `chart` (legacy, bodyless)              | a `\[Chart: title\]` token (no recoverable data)                                                                                                                            |
| `inline-media-image` (migration)        | the standard media rendering: `![alt](url)` via `mediaResolver`, else the `media:` placeholder                                                                              |
| `syncBlock` / `bodiedSyncBlock`         | a `\[Sync block\]` / `\[Sync block: <resourceId>\]` token (the bodied variant also renders its body)                                                                        |
| custom extension                        | your `ExtensionRenderer` output, else an `\[Extension: type/key\]` placeholder + a `WARNING` diagnostic                                                                     |
| unrecognized node                       | governed by [`unknownNodePolicy`](#options-markdownoptions)                                                                                                                 |

## Diagnostics

A `Diagnostic` is `(String code, String message, @Nullable Throwable cause, Severity severity)` where `Severity` is `INFO | WARNING | ERROR`. The parse, analyze, and render phases each contribute diagnostics; they are concatenated in that order into `MarkdownResult.diagnostics()` (parse-only diagnostics also appear as `ParseResult.issues()`). `MarkdownResult.wasLossy()` is `true` iff any diagnostic is `WARNING` or `ERROR`; it deliberately ignores options-driven by-design loss (see [the catalogue below](#lossy-and-by-design-behaviours)).

| Severity  | Meaning                                      | Example                                                                      |
| --------- | -------------------------------------------- | ---------------------------------------------------------------------------- |
| `INFO`    | Non-lossy note                               | An unknown node preserved as raw JSON under `PRESERVE_RAW`                   |
| `WARNING` | Content converted but altered or dropped     | Unsupported macro placeholdered; unknown mark dropped; `UNSUPPORTED_VERSION` |
| `ERROR`   | Conversion aborted or produced an empty body | `INVALID_JSON`; a structural validation failure                              |

Notable parse codes (from `RootValidator` and the parser):

| Code                  | Severity  | Raised when                                                         |
| --------------------- | --------- | ------------------------------------------------------------------- |
| `INVALID_JSON`        | `ERROR`   | The payload is not valid JSON, or exceeds the depth-100 nesting cap |
| `MISSING_DOCUMENT`    | `ERROR`   | The parsed document is `null`                                       |
| `INVALID_ROOT_TYPE`   | `ERROR`   | The root is not a JSON object                                       |
| `INVALID_ROOT_NODE`   | `ERROR`   | The root `type` is not `"doc"`                                      |
| `INVALID_VERSION`     | `ERROR`   | The root has no integer `version`                                   |
| `INVALID_CONTENT`     | `ERROR`   | The root `content` field is not an array                            |
| `UNSUPPORTED_VERSION` | `WARNING` | The root `version` is not `1` (parse proceeds best-effort)          |

## Lossy and by-design behaviours

These outcomes are expected results of the chosen options or of GFM's expressive limits. None set `wasLossy()` *unless* noted as raising a `WARNING`/`ERROR` diagnostic.

| Behaviour                            | Detail                                                                                                                                                                                                                                                                                                                                                       |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Visual marks dropped                 | Colour, background, border, font size, and block alignment have no GFM equivalent and are dropped unless `htmlVisualMarks` is enabled.                                                                                                                                                                                                                       |
| Table HTML fallback                  | Tables with colspan/rowspan, a number column, block-level cell content, or a non-canonical header are emitted as raw HTML `<table>`. Empty rows are silently dropped and short rows padded to a rectangle.                                                                                                                                                   |
| `GFM_PROMOTE_FIRST_ROW` row demotion | The default `tableFallback` promotes a header-less table's first row to a header, so a single-data-row table loses its only row to a heading. Use `GFM_EMPTY_HEADER` to keep all rows as data.                                                                                                                                                               |
| Media/attachment placeholders        | Without a resolver, file media and attachment macros render to inert `media:<collection>/<id>` / `attachment:<fileId>` destinations.                                                                                                                                                                                                                         |
| Unsupported macros                   | A macro with no built-in or custom renderer becomes a labelled placeholder and a `WARNING` diagnostic (**lossy**). The placeholder is logged at WARN once per extension type/key per converter, then at DEBUG.                                                                                                                                               |
| Dynamic page-list macros             | `pagetree`/`children` list pages Confluence assembles server-side; the ADF carries only the reference. They emit a `{{pagetree}}`/`{{children}}` token (**not** lossy) unless a `pageTreeResolver` expands them. Fallbacks are reported in `MarkdownResult.unresolved().pageTreeRefs()`.                                                                     |
| Excerpts                             | `excerpt` renders its body transparently and exposes the region as `ContentMetadata.excerpts()`. `excerpt-include` composes content server-side; without an `excerptResolver` it emits an `[Excerpt include: <page>]` placeholder (**not** lossy) and is reported in `unresolved().excerptRefs()`. The reference carries the source page *title*, not an id. |
| Charts                               | Legacy bodied `chart` renders an italic caption plus its body data table (numbers survive). The modern chart app draws from a table elsewhere in the document — the node contributes only the caption, never a placeholder. Only a legacy bodyless `chart:default` keeps a `[Chart: <title>]` placeholder.                                                   |
| Migration macros                     | `inline-media-image` is a media node in disguise; it renders through the standard media path and its file id appears in `ContentMetadata.referencedFileIds()`.                                                                                                                                                                                               |

## URL handling and safety

ADF input is untrusted. Link, card, media, and macro **destinations are scheme-sanitized** against a safe allow-list:

```text
http  https  mailto  tel  ftp  ftps  media  attachment
```

(`media` and `attachment` are the library's own inert placeholder schemes.) A destination whose scheme is not on the list — `javascript:`, `data:`, `vbscript:`, `file:` — has its scheme colon percent-encoded so it can no longer execute. Control-character obfuscation (tabs/newlines smuggled inside the scheme) is stripped first, and the HTML table-fallback renderer sanitizes URLs as defence in depth.

Two exceptions are emitted **verbatim** and are the caller's responsibility to escape:

- **`ExtensionRenderer` output** — a custom renderer's string is inserted as-is.
- **`ExcerptResolver` output** — the resolved excerpt Markdown is inserted as-is.

Separately, **`htmlVisualMarks` does not sanitize CSS.** Attacker-controlled `textColor`/`fontSize`/etc. values flow into a `<span style="…">` with only HTML-attribute breakout blocked; the CSS itself is not validated. Leave it off when rendering untrusted input, or sanitize the output.

## CLI

The `adf4j` binary exposes three subcommands, each mapping onto one library method:

| Command    | Library method | Output                                                       |
| ---------- | -------------- | ------------------------------------------------------------ |
| `convert`  | `convert(...)` | Markdown body on stdout (or full result JSON with `-f json`) |
| `analyze`  | `analyze(...)` | references, attachments, and outline as JSON (or `-f text`)  |
| `validate` | `parse(...)`   | parse diagnostics; exit code reflects validity               |

```text
adf4j <command> [options] [<input-file>]
```

Input comes from `<input-file>` or, when no path is given, stdin. Stdout carries only the deliverable; diagnostics and warnings go to stderr. `-o` writes atomically (temp file + rename). A bare `adf4j` (or `-h`) prints help.

**Global flags** (after the command):

| Flag                | Effect                                               |
| ------------------- | ---------------------------------------------------- |
| `-h, --help`        | Show help (per subcommand too)                       |
| `-V, --version`     | Show the version                                     |
| `-v, --verbose`     | Show stack traces on error                           |
| `-q, --quiet`       | Suppress the stderr diagnostics summary and warnings |
| `-o, --output FILE` | Write to FILE (atomically) instead of stdout         |

### convert

| Flag                         | Effect                                                                                              |
| ---------------------------- | --------------------------------------------------------------------------------------------------- |
| `-f, --format md\|json`      | `md` (default) prints the body; `json` prints `{body, wasLossy, diagnostics, metadata, unresolved}` |
| `--compact`                  | Single-line JSON instead of pretty                                                                  |
| `--fail-on-lossy`            | Exit 4 when the result is lossy (any WARNING/ERROR diagnostic)                                      |
| `-t, --title TITLE`          | Prepend TITLE as a level-1 (`#`) heading (`documentTitle`)                                          |
| `-c, --collapse-hard-breaks` | Render hard breaks as soft breaks (`collapseHardBreaks`)                                            |
| `-p, --escape-parentheses`   | Backslash-escape literal `(` and `)` (`escapeParentheses`)                                          |
| `--image-size`               | Emit non-GFM image `{width= height=}` attributes (`imageSizeAttributes`)                            |
| `--html-visual-marks`        | Keep visual-only marks as inline `<span style>` (`htmlVisualMarks`)                                 |
| `--unknown-nodes V`          | `placeholder` (default) \| `skip` \| `fail` \| `preserve-raw` (`unknownNodePolicy`)                 |
| `--table-fallback V`         | `gfm-promote-first-row` (default) \| `gfm-empty-header` \| `html` (`tableFallback`)                 |

### analyze

| Flag                      | Effect                                                                                                                                                                                                                 |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `-f, --format json\|text` | `json` (default) \| human-readable `text`                                                                                                                                                                              |
| `--compact`               | Single-line JSON                                                                                                                                                                                                       |
| `--select SECTIONS`       | Comma-separated subset of: `pageRefs`, `externalRefs`, `mentionRefs`, `attachmentRefs`, `referencedFileIds`, `pageTreeRefs`, `excerptRefs`, `excerpts`, `outline` (default: all). `excerpts` are rendered to Markdown. |
| `--attachments-map FILE`  | Supply the attachment inventory so macro-based attachment file ids appear in `referencedFileIds` — without it they are silently absent.                                                                                |

The convert rendering/resolver flags are also accepted (they affect excerpt rendering).

### validate

| Flag                      | Effect                                                    |
| ------------------------- | --------------------------------------------------------- |
| `-f, --format text\|json` | `text` (default) \| `json` (`{validAdfRoot, issues}`)     |
| `--compact`               | Single-line JSON                                          |
| `--fail-on-warning`       | Exit 4 when a WARNING is present (else 0 on a valid root) |

### Resolver flags

Data-driven hooks, accepted by `convert` and `analyze`. A `*-url` template substitutes its `{placeholder}`s (each substituted value is percent-encoded, then the library scheme-sanitizes the whole URL); a `*-map` file is a JSON lookup. Where both are given, **the map wins on a hit** and the template is the fallback. An unknown `{placeholder}` in a template is a usage error (exit 1), raised before any conversion runs. Map values are absolute, trusted URLs — they are passed through with scheme sanitization only (a scheme-relative or relative value is emitted as-is).

| Flag                     | Maps to                   | Schema                                                   |
| ------------------------ | ------------------------- | -------------------------------------------------------- |
| `--media-url TPL`        | `MediaResolver`           | template; placeholders `{id}` `{collection}` `{localId}` |
| `--media-map FILE`       | `MediaResolver`           | `{ "<fileId>": "https://…" }`                            |
| `--attachment-url TPL`   | `AttachmentResolver`      | template; placeholders `{fileId}` `{title}`              |
| `--attachment-map FILE`  | `AttachmentResolver`      | `{ "<fileId>": "https://…" }`                            |
| `--page-url TPL`         | `PageLinkResolver`        | template; placeholder `{pageId}`                         |
| `--page-map FILE`        | `PageLinkResolver`        | `{ "<pageNodeId>": "https://…" }`                        |
| `--page-tree-map FILE`   | `PageTreeResolver`        | see below                                                |
| `--excerpt-map FILE`     | `ExcerptResolver`         | see below (**verbatim**)                                 |
| `--attachments-map FILE` | `ConfluenceRenderContext` | `[ { "fileId": "…", "title": "…", "mediaType": "…" } ]`  |
| `--extension-map FILE`   | `ExtensionRenderer`       | see below (**verbatim**)                                 |

An **absent** key/entry declines the lookup (placeholder kept, recorded on `unresolved()`); a **present** entry is an answer — including the empty answers an empty `markdown` string (suppresses the excerpt) and an empty page-tree array (renders nothing) express. A **blank** value in a URL map (`--media-map`, `--attachment-map`, `--page-map`) is itself a decline — it falls through to the template, or to the placeholder, per the null/blank decline convention.

`--page-tree-map` is keyed by macro kind, then root page id (`""` for a rootless `@self`-style macro); each entry carries `depth`/`title`/`pageNodeId`:

```json
{ "pagetree": { "<root>": [ { "depth": 0, "title": "Overview", "pageNodeId": "123" } ] },
  "children": { "<root>": [] } }
```

`--excerpt-map` is an array; `name` is the excerpt selector (`null` = the page's unnamed excerpt), and `page` keeps any `SPACEKEY:` prefix verbatim:

```json
[ { "page": "DOCS:Onboarding", "name": "summary", "markdown": "…" },
  { "page": "Roadmap", "name": null, "markdown": "…" } ]
```

`--extension-map` is an array matched by `key` (and optional `type`); `template` interpolates `ExtensionContext` parameters via `{param}`:

```json
[ { "type": "com.example", "key": "chart", "template": "![chart]({src})" } ]
```

`--excerpt-map` and `--extension-map` values are emitted **verbatim and not HTML-sanitized**; use only trusted files (the CLI prints a stderr warning when either is used).

## Exit codes

| Code | Meaning                                                                                               |
| ---- | ----------------------------------------------------------------------------------------------------- |
| `0`  | success                                                                                               |
| `1`  | usage error (unknown flag/subcommand, unknown `{placeholder}`, malformed/invalid map file)            |
| `2`  | I/O error (input or map file not found/unreadable, output write failure)                              |
| `3`  | content failure (`validate` invalid root or ERROR diagnostic; `convert --unknown-nodes fail` aborted) |
| `4`  | quality gate (`convert --fail-on-lossy` on a lossy result, or `validate --fail-on-warning`)           |
| `70` | unexpected internal error (a bug)                                                                     |

Malformed ADF alone does **not** cause a non-zero exit from `convert`: it never throws on bad input, so it prints an empty body and exits `0`. Use `-f json` to surface the `INVALID_JSON` diagnostic, or `--fail-on-lossy` / `validate` to gate on it.

## Distribution

adf4j ships as GraalVM **native executables** (Linux, macOS, Windows) plus a **WebAssembly** build, attached to each GitHub release. The CLI is reflection-free and uses Jackson in tree mode only, so it adds no native-image metadata.

The CLI jar is **not** a standalone fat jar. To run it on the JVM, put adf4j and its dependencies — Jackson, CommonMark, jsoup, SLF4J — on the class/module path yourself.

The WASM build (a separate `adf4j-wasm` module, built under `-Pwasm` with an Oracle GraalVM JDK 25) exposes string→string functions to a JS host via a `globalThis` bridge — no JVM, no stdin, no host filesystem. From Node, `loadAdf4j()` yields `{ version(), convert(json) -> md, convertJson(json) -> { ok, lossy, warnings, errors, body } }`.
