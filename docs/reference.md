# Reference

Lookup tables for options, ADF to Markdown rendering, diagnostics, URL safety, CLI flags, and exit codes. For task recipes, see the [guide](./guide.md). For internals, see the [architecture](./architecture.md).

## Options (MarkdownOptions)

`MarkdownOptions` is immutable. Build it with `MarkdownOptions.defaults()` plus `withX(...)`, `MarkdownOptions.builder()`, or `toBuilder()`. Bind options with `AdfToMarkdown.with(options)`, or pass them to a per-call `convert`, `analyze`, or `toMarkdown` overload.

| Option                | Default                 | Effect                                                                                                                                  |
| --------------------- | ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `unknownNodePolicy`   | `PLACEHOLDER`           | Renders unknown nodes as a placeholder, skips them, preserves raw JSON, or fails. Unknown marks always drop with a `WARNING`.           |
| `confluenceContext`   | empty                   | Supplies the page attachment inventory for attachment macros, media file nodes, and the `attachments` macro. Entries with a `downloadUrl` become the default link destination. An empty supplied inventory is authoritative. |
| `imageSizeAttributes` | `false`                 | Emits non-GFM `{width= height=}` image attributes.                                                                                      |
| `tableFallback`       | `GFM_PROMOTE_FIRST_ROW` | Controls headerless GFM tables: promote first row, synthesize an empty header, or force HTML. Complex tables always use HTML.           |
| `mediaResolver`       | `null`                  | Turns media IDs into URLs. `null` or blank keeps the `media:<collection>/<id>` placeholder.                                             |
| `htmlVisualMarks`     | `false`                 | Preserves visual marks as inline HTML. CSS values are not sanitized.                                                                    |
| `extensionRenderers`  | empty                   | Custom renderers consulted before built-ins. First non-null result wins. Output is inserted verbatim.                                   |
| `attachmentResolver`  | `null`                  | Turns resolved `attachment:<fileId>` references into URLs. `null` or blank keeps the placeholder.                                       |
| `pageLinkResolver`    | `null`                  | Rewrites Confluence page node IDs. `null` or blank keeps the original href or plain page-tree text.                                     |
| `pageTreeResolver`    | `null`                  | Expands `pagetree` and `children` macros. An empty list means no descendants. `null` keeps the token.                                   |
| `excerptResolver`     | `null`                  | Expands `excerpt-include` macros. Output is inserted verbatim. Empty string suppresses output. `null` keeps the placeholder.            |
| `collapseHardBreaks`  | `false`                 | Renders hard breaks as plain newlines instead of two-space GFM hard breaks.                                                             |
| `documentTitle`       | `null`                  | Prepends a level-1 heading. Newlines collapse to spaces and Markdown punctuation is escaped.                                            |
| `escapeParentheses`   | `false`                 | Backslash-escapes literal `(` and `)` in text and image alt text.                                                                       |

A `RuntimeException` from any resolver or extension renderer is caught, logged, and treated as a declined lookup.

## ADF to Markdown mapping

Default rendering behavior. Markdown escaping, anchors, autolinks, and URL sanitization are applied automatically.

### Blocks

| ADF construct                          | Rendered as                                                                          |
| -------------------------------------- | ------------------------------------------------------------------------------------ |
| `heading` levels 1 to 6                | `#` through `######`; higher levels clamp to 6. TOC-referenced headings get anchors. |
| `paragraph`                            | Text block.                                                                          |
| `bulletList` / `orderedList`           | `-` / `1.` items, with nesting and `order` honored.                                  |
| `taskList`                             | `- [x]` or `- [ ]`.                                                                  |
| `decisionList`                         | Escaped decision labels, such as `\[decision:DECIDED\]`.                             |
| `blockquote`                           | `>` quote blocks.                                                                    |
| `codeBlock`                            | Fenced code block with a widened fence when needed.                                  |
| `panel`                                | GFM alert: note, warning, caution, or tip.                                           |
| `rule`                                 | `---`.                                                                               |
| `table`                                | GFM pipe table or HTML fallback.                                                     |
| `expand` / `nestedExpand`              | `<details><summary>...</summary>...</details>`.                                      |
| `layoutSection` / `layoutColumn`       | Columns flattened into sequential blocks.                                            |
| `mediaSingle` / `mediaGroup` / `media` | Image or file link through `mediaResolver`, else a `media:` placeholder.             |

### Inlines and marks

| ADF construct                            | Rendered as                                                                         |
| ---------------------------------------- | ----------------------------------------------------------------------------------- |
| `text`                                   | Escaped Markdown text. Parentheses escape only when `escapeParentheses` is enabled. |
| `strong` / `em` / `strike` / `code`      | `**bold**`, `*italic*`, `~~strike~~`, or backtick code.                             |
| `underline` / `subsup`                   | HTML `<u>`, `<sub>`, or `<sup>`.                                                    |
| `link`                                   | `[text](url)` with URL scheme sanitization.                                         |
| Visual marks                             | Dropped by default, or inline HTML when `htmlVisualMarks` is enabled.               |
| `hardBreak`                              | Two-space GFM hard break, or soft newline with `collapseHardBreaks`.                |
| `mention`                                | `@DisplayName`.                                                                     |
| `emoji`                                  | Unicode glyph, or shortname when no glyph exists.                                   |
| `date`                                   | ISO `YYYY-MM-DD`.                                                                   |
| `status`                                 | Escaped bracket label, such as `\[Blocked\]`.                                       |
| `placeholder`                            | Escaped plain placeholder text.                                                     |
| `inlineCard` / `blockCard` / `embedCard` | Autolink, link, title text, or escaped card token.                                  |
| `mediaInline`                            | Inline image or file link through `mediaResolver`, else a `media:` placeholder.     |

### Confluence macros and extensions

| Macro / extension               | Rendered as                                                                                              |
| ------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `toc`                           | Nested bullet list of heading links.                                                                     |
| `anchor`                        | `<a id="..."></a>`.                                                                                      |
| `pagetree` / `children`         | Resolver-supplied page list, else `{{pagetree}}` or `{{children}}`.                                      |
| `excerpt`                       | Its body, also exposed as `ContentMetadata.excerpts()`.                                                  |
| `excerpt-include`               | `excerptResolver` Markdown, else an escaped placeholder.                                                 |
| `attachments`                   | Links from the supplied attachment inventory, else extension placeholder when no inventory was supplied. |
| `viewpdf` / view file           | PDF/file link once its title resolves against `ConfluenceRenderContext`, else an escaped token.          |
| `iframe`                        | `[Embedded content](url)`.                                                                               |
| Bodied `chart`                  | Italic caption plus the body data table.                                                                 |
| Modern `com.atlassian.chart`    | Italic caption; the data table renders wherever its own node appears.                                    |
| Legacy bodyless `chart`         | Escaped chart placeholder.                                                                               |
| `inline-media-image`            | Standard media rendering.                                                                                |
| `syncBlock` / `bodiedSyncBlock` | Escaped sync-block token; bodied sync blocks also render their body.                                     |
| Custom extension                | `ExtensionRenderer` output, else extension placeholder plus `WARNING`.                                   |
| Unknown node                    | Controlled by `unknownNodePolicy`.                                                                       |

## Diagnostics

`Diagnostic` contains `code`, `message`, optional `cause`, and `severity`. `MarkdownResult.diagnostics()` concatenates parse, analyze, and render diagnostics. `MarkdownResult.wasLossy()` is true when any diagnostic is `WARNING` or `ERROR`.

| Severity  | Meaning                                      | Example                             |
| --------- | -------------------------------------------- | ----------------------------------- |
| `INFO`    | Non-lossy note.                              | Unknown node preserved as raw JSON. |
| `WARNING` | Content converted but changed or dropped.    | Unsupported macro placeholdered.    |
| `ERROR`   | Conversion failed or produced an empty body. | Invalid JSON.                       |

Parse codes:

| Code                  | Severity  | Raised when                                                     |
| --------------------- | --------- | --------------------------------------------------------------- |
| `INVALID_JSON`        | `ERROR`   | Payload is not valid JSON or exceeds the depth-100 nesting cap. |
| `MISSING_DOCUMENT`    | `ERROR`   | Parsed document is `null`.                                      |
| `INVALID_ROOT_TYPE`   | `ERROR`   | Root is not a JSON object.                                      |
| `INVALID_ROOT_NODE`   | `ERROR`   | Root `type` is not `"doc"`.                                     |
| `INVALID_VERSION`     | `ERROR`   | Root has no integer `version`.                                  |
| `INVALID_CONTENT`     | `ERROR`   | Root `content` is not an array.                                 |
| `UNSUPPORTED_VERSION` | `WARNING` | Root `version` is not `1`; parsing continues best effort.       |

## Lossy and by-design behaviours

These outcomes are expected for the selected options or for GFM limits. They do not set `wasLossy()` unless the row says they raise a `WARNING` or `ERROR`.

| Behaviour                             | Detail                                                                                                                                                            |
| ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Visual marks dropped                  | Color, background, border, font size, and alignment have no GFM equivalent unless `htmlVisualMarks` is enabled.                                                   |
| Table HTML fallback                   | Tables with spans, number columns, block-level cell content, or non-canonical headers use raw HTML.                                                               |
| `GFM_PROMOTE_FIRST_ROW` row promotion | The default table fallback promotes the first row of a headerless table to the header. Use `GFM_EMPTY_HEADER` to keep all rows as data.                           |
| Media and attachment placeholders     | Without an attachment inventory carrying `downloadUrl`s or resolvers, media and attachments render to inert `media:` or `attachment:` destinations.               |
| Unsupported macros                    | No built-in or custom renderer means placeholder plus `WARNING`.                                                                                                  |
| Dynamic page-list macros              | `pagetree` and `children` need caller-supplied hierarchy. Without it they emit tokens and are recorded in `unresolved()`, but are not lossy.                      |
| Excerpts                              | `excerpt` renders its body. `excerpt-include` needs `excerptResolver`; without it, the placeholder is recorded in `unresolved()` but is not lossy.                |
| Charts                                | Bodied charts keep caption and body data. Modern charts keep caption only because their data table is a separate node. Legacy bodyless charts keep a placeholder. |
| Migration macros                      | `inline-media-image` follows normal media rendering and contributes its file ID to metadata.                                                                      |

## URL handling and safety

ADF input is untrusted. Link, card, media, and macro destinations are scheme-sanitized against this allow-list:

```text
http  https  mailto  tel  ftp  ftps  media  attachment
```

Unsafe schemes such as `javascript:`, `data:`, `vbscript:`, and `file:` have the scheme colon percent-encoded after control-character cleanup. The HTML table fallback also sanitizes URLs.

Caller-provided Markdown from `ExtensionRenderer` and `ExcerptResolver` is inserted verbatim. Escape or sanitize untrusted values before returning them.

`htmlVisualMarks` does not sanitize CSS. Leave it off for untrusted input unless the final output is sanitized elsewhere.

## CLI

```text
adf4j <command> [options] [<input-file>]
```

Input comes from `<input-file>` or stdin. Stdout contains only the requested output. Diagnostics and warnings go to stderr. `-o` writes atomically.

| Command    | Library method | Output                                          |
| ---------- | -------------- | ----------------------------------------------- |
| `convert`  | `convert(...)` | Markdown body, or full JSON with `-f json`.     |
| `analyze`  | `analyze(...)` | Metadata JSON, or text with `-f text`.          |
| `validate` | `parse(...)`   | Parse diagnostics; exit code reflects validity. |

Global flags after the command:

| Flag                | Effect                                            |
| ------------------- | ------------------------------------------------- |
| `-h, --help`        | Show help.                                        |
| `-V, --version`     | Show version.                                     |
| `-v, --verbose`     | Show stack traces on error.                       |
| `-q, --quiet`       | Suppress stderr diagnostics summary and warnings. |
| `-o, --output FILE` | Write output to `FILE` instead of stdout.         |

### convert

| Flag                         | Effect                                                                                                  |
| ---------------------------- | ------------------------------------------------------------------------------------------------------- |
| `-f, --format md\|json`      | `md` prints the body. `json` prints body, `wasLossy`, diagnostics, metadata, and unresolved references. |
| `--compact`                  | Single-line JSON.                                                                                       |
| `--fail-on-lossy`            | Exit 4 when the result is lossy.                                                                        |
| `-t, --title TITLE`          | Prepend a level-1 heading.                                                                              |
| `-c, --collapse-hard-breaks` | Render hard breaks as soft breaks.                                                                      |
| `-p, --escape-parentheses`   | Escape literal `(` and `)`.                                                                             |
| `--image-size`               | Emit non-GFM image size attributes.                                                                     |
| `--html-visual-marks`        | Keep visual marks as inline HTML.                                                                       |
| `--unknown-nodes V`          | `placeholder`, `skip`, `fail`, or `preserve-raw`.                                                       |
| `--table-fallback V`         | `gfm-promote-first-row`, `gfm-empty-header`, or `html`.                                                 |

### analyze

| Flag                      | Effect                                                                                                                                                            |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `-f, --format json\|text` | Output format.                                                                                                                                                    |
| `--compact`               | Single-line JSON.                                                                                                                                                 |
| `--select SECTIONS`       | Comma-separated subset of `pageRefs`, `externalRefs`, `mentionRefs`, `attachmentRefs`, `referencedFileIds`, `pageTreeRefs`, `excerptRefs`, `excerpts`, `outline`. |
| `--attachments-map FILE`  | Supplies attachment inventory so attachment macros can contribute file IDs.                                                                                       |

`analyze` also accepts the convert resolver flags when they affect metadata or excerpt rendering.

### validate

| Flag                      | Effect                            |
| ------------------------- | --------------------------------- |
| `-f, --format text\|json` | Output format.                    |
| `--compact`               | Single-line JSON.                 |
| `--fail-on-warning`       | Exit 4 when a warning is present. |

### Resolver flags

Resolver flags are accepted by `convert` and `analyze`. A `*-url` template substitutes placeholders after percent-encoding substituted values. A `*-map` file is a JSON lookup. If both are supplied, a map hit wins and the template is fallback. Unknown template placeholders are usage errors.

| Flag                     | Maps to                   | Schema                                                        |
| ------------------------ | ------------------------- | ------------------------------------------------------------- |
| `--media-url TPL`        | `MediaResolver`           | Template with `{id}`, `{collection}`, `{localId}`.            |
| `--media-map FILE`       | `MediaResolver`           | `{ "<fileId>": "https://example.com/file" }`                  |
| `--attachment-url TPL`   | `AttachmentResolver`      | Template with `{fileId}`, `{title}`.                          |
| `--attachment-map FILE`  | `AttachmentResolver`      | `{ "<fileId>": "https://example.com/file" }`                  |
| `--page-url TPL`         | `PageLinkResolver`        | Template with `{pageId}`.                                     |
| `--page-map FILE`        | `PageLinkResolver`        | `{ "<pageNodeId>": "https://example.com/page" }`              |
| `--page-tree-map FILE`   | `PageTreeResolver`        | See below.                                                    |
| `--excerpt-map FILE`     | `ExcerptResolver`         | See below; values are verbatim.                               |
| `--attachments-map FILE` | `ConfluenceRenderContext` | `[ { "fileId": "...", "title": "...", "mediaType": "...", "downloadUrl": "..." } ]` |
| `--extension-map FILE`   | `ExtensionRenderer`       | See below; values are verbatim.                               |

Absent entries decline the lookup. Present entries are answers, including empty page-tree arrays and empty excerpt Markdown. Blank values in URL maps decline and fall through to the template or placeholder.

`--page-tree-map`:

```json
{
  "pagetree": { "<root>": [ { "depth": 0, "title": "Overview", "pageNodeId": "123" } ] },
  "children": { "<root>": [] }
}
```

`--excerpt-map`:

```json
[
  { "page": "DOCS:Onboarding", "name": "summary", "markdown": "..." },
  { "page": "Roadmap", "name": null, "markdown": "..." }
]
```

`--extension-map`:

```json
[
  { "type": "com.example", "key": "chart", "template": "![chart]({src})" }
]
```

The CLI warns on stderr when `--excerpt-map` or `--extension-map` is used because their Markdown is not sanitized.

## Exit codes

| Code | Meaning                                                              |
| ---- | -------------------------------------------------------------------- |
| `0`  | Success.                                                             |
| `1`  | Usage error, unknown flag, unknown placeholder, or invalid map file. |
| `2`  | I/O error.                                                           |
| `3`  | Content failure, such as invalid root or `--unknown-nodes fail`.     |
| `4`  | Quality gate, such as `--fail-on-lossy` or `--fail-on-warning`.      |
| `70` | Unexpected internal error.                                           |

`convert` exits 0 on malformed ADF unless you add `--fail-on-lossy`. It prints an empty body for invalid input. Use `-f json`, `--fail-on-lossy`, or `validate` when you need diagnostics to affect control flow.

## Distribution

Releases include GraalVM native executables for Linux, macOS, and Windows, plus a WebAssembly build.

The CLI jar is not a standalone fat jar. To run it on the JVM, put adf4j and its dependencies on the class or module path.

The WASM build is produced by the `adf4j-wasm` module under `-Pwasm` with Oracle GraalVM JDK 25. It exposes string-to-string functions to a JavaScript host through a `globalThis` bridge: `version()`, `convert(json)`, and `convertJson(json)`.
