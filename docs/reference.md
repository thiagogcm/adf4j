# Reference

Lookup tables for options, ADF to Markdown rendering, diagnostics, URL safety, CLI flags, and exit codes. For task recipes, see the [guide](./guide.md). For internals, see the [architecture](./architecture.md).

## Options (ConversionOptions)

`ConversionOptions` groups immutable `MarkdownOptions`, `DocumentContext`, and `ResolverHooks`. Start from `ConversionOptions.defaults()` and chain `withX(...)`, each of which returns a new instance. Bind options with `AdfToMarkdown.with(options)`; there are no per-call overloads, and a converter is cheap to create.

| Option | Default | Effect |
| --- | --- | --- |
| `unknownNodePolicy` | `PLACEHOLDER` | Renders unknown nodes as a placeholder, skips them, preserves raw JSON, or fails. Unknown marks always drop. |
| `attachments` | `null` | The page's attachment inventory, for attachment macros, media file nodes, and the `attachments` macro. `null` means unknown; a list, even an empty one, is authoritative. Entries are keyed by `fileId`: a blank one is ignored and the first per id wins. Ids and titles match trimmed and case-insensitively. A `downloadUrl` becomes the default link destination. |
| `imageSizeAttributes` | `false` | Emits non-GFM `{width= height=}` image attributes. |
| `tableFallback` | `GFM_EMPTY_HEADER` | Controls headerless GFM tables: promote first row, synthesize an empty header (`GFM_EMPTY_HEADER`), or force `HTML`. Complex tables always use HTML. |
| `mediaResolver` | `null` | Turns media nodes into URLs. Not consulted for a node with its own `url`. `null` or blank falls back to the inventory `downloadUrl`, then the `media:<collection>/<id>` placeholder. |
| `htmlVisualMarks` | `false` | Preserves visual marks as inline HTML. CSS values are allowlisted; see [URL handling and safety](#url-handling-and-safety). |
| `extensionRenderers` | empty | Custom renderers consulted before built-ins. First non-defer result wins; use explicit replace or header output. Output is inserted verbatim. |
| `attachmentResolver` | `null` | Turns attachment macro references into URLs. `null` or blank falls back to the entry's `downloadUrl`, then the `attachment:<fileId>` placeholder. Not consulted for media nodes. |
| `pageLinkResolver` | `null` | Rewrites Confluence page node IDs. `null` or blank keeps the original href or plain page-tree text. |
| `pageTreeResolver` | `null` | Expands `pagetree` and `children` macros. An empty list means no descendants. `null` keeps the token. |
| `excerptResolver` | `null` | Expands `excerpt-include` macros. Output is inserted verbatim. Empty string suppresses output. `null` keeps the placeholder. |
| `collapseHardBreaks` | `false` | Renders hard breaks as plain newlines instead of two-space GFM hard breaks. |
| `documentTitle` | `null` | Prepends a level-1 heading to a successful conversion, including an empty document. Newlines collapse to spaces and Markdown punctuation is escaped. Blank emits nothing. Not reflected in metadata. |
| `escapeParentheses` | `false` | Backslash-escapes literal `(` and `)` in text and image alt text. |

A `RuntimeException` from a resolver or renderer records `CALLBACK_FAILED` and uses the fallback. A shared `fileResolver` handles media and attachment file IDs after a specialized hook declines.

Additional conversion flags: `--config FILE` supplies portable configuration, `--fail-on-changes` gates non-empty effects, and `--fail-on-unresolved` gates missing output dependencies. See [migration](migration-2.md).

New error codes are `EMPTY_INPUT`, `INVALID_OPTIONS`, `SCHEMA_VIOLATION`, and `UNKNOWN_NODE_REJECTED`. `CALLBACK_FAILED` is a warning. Java results expose `isSuccess()`, `hasErrors()`, and `hasWarnings()`.

## ADF to Markdown mapping

Default rendering behavior. Markdown escaping, anchors, autolinks, and URL sanitization are applied automatically.

### Blocks

| ADF construct | Rendered as |
| --- | --- |
| `heading` levels 1 to 6 | `#` through `######`; other levels clamp into the range. An `<a id>` precedes any heading at a level a `toc` macro covers, and any with an explicit `anchor` macro. |
| `paragraph` | Text block. |
| `bulletList` / `orderedList` | `-` / `1.` items, with nesting and `order` honored. |
| `taskList` | `- [x]` or `- [ ]`. |
| `decisionList` | Items with an escaped decision label, such as `- \[decision:DECIDED\] Ship it`. |
| `blockquote` | `>` quote blocks. |
| `codeBlock` | Fenced code block with a widened fence when needed. |
| `panel` | GFM alert: note, warning, caution, or tip. |
| `rule` | `---`. |
| `table` | GFM pipe table or HTML fallback. |
| `expand` / `nestedExpand` | `<details><summary>...</summary>...</details>`. |
| `layoutSection` / `layoutColumn` | Columns flattened into sequential blocks. |
| `mediaSingle` / `mediaGroup` / `media` | Image or file link. The destination is the node's `url`, else `mediaResolver`, else the inventory `downloadUrl`, else a `media:` placeholder. |

A media node is an image when its `__fileMimeType` or `mediaType` attribute says so, else the inventory entry's `mediaType`, else a file-name extension. Otherwise it renders as a link. The media `type` attribute (`file`, `link`, `external`) plays no part.

### Inlines and marks

| ADF construct | Rendered as |
| --- | --- |
| `text` | Escaped Markdown text. Parentheses escape only when `escapeParentheses` is enabled. |
| `strong` / `em` / `strike` / `code` | `**bold**`, `*italic*`, `~~strike~~`, or backtick code. |
| `underline` / `subsup` | HTML `<u>`, `<sub>`, or `<sup>`. |
| `link` | `[text](url)` with URL scheme sanitization. |
| Visual marks | Dropped by default. With `htmlVisualMarks`, one combined `<span style>`, or `<div align>` for `alignment`. |
| `indentation` | Non-breaking spaces before a paragraph or heading, 4 per level up to level 6. |
| `annotation` / `breakout` / `fragment` / `dataConsumer` | No effect on the output. |
| `hardBreak` | Two-space GFM hard break, or soft newline with `collapseHardBreaks`. A space in a heading, a `<br>` in a table cell. |
| `mention` | `@DisplayName`, else `@<id>`, else `@unknown`. |
| `emoji` | Unicode glyph, or shortname when no glyph exists. |
| `date` | ISO `YYYY-MM-DD` in UTC. |
| `status` | Escaped bracket label, such as `\[Blocked\]`. |
| `placeholder` | Escaped plain placeholder text. |
| `inlineCard` / `blockCard` / `embedCard` | Autolink, link, title text, or escaped card token. |
| `mediaInline` | Inline image or file link, resolved like `media`. |

### Confluence macros and extensions

| Macro / extension | Rendered as |
| --- | --- |
| `toc` | Nested bullet list of heading links. |
| `anchor` | `<a id="..."></a>`. |
| `pagetree` / `children` | Resolver-supplied page list, else `{{pagetree}}`, `{{pagetree:<root>}}`, `{{children}}`, or `{{children:<depth>}}`. |
| `excerpt` | Its body, also exposed as `ContentMetadata.excerpts()`. |
| `excerpt-include` | `excerptResolver` Markdown, else an escaped placeholder. |
| `attachments` | Links from the supplied attachment inventory, else extension placeholder when no inventory was supplied. |
| `viewpdf` | `[PDF: <name>](url)` once it carries a file id or its name matches the attachment inventory, else `\[PDF: <name>\]`. |
| `iframe` | `[Embedded content](url)`, or `\[Embedded content\]` without a `src`. |
| Bodied `chart` or `chart:*` | Italic caption plus the body data table. |
| Modern `com.atlassian.chart` | Italic caption; the data table renders wherever its own node appears. |
| Bodyless `chart:default` | Escaped `\[Chart: <title>\]` label. Any other bodyless legacy key falls back like a custom extension. |
| `inline-media-image` | Standard media rendering. |
| `syncBlock` / `bodiedSyncBlock` | Escaped sync-block token; bodied sync blocks also render their body. |
| Custom extension | `ExtensionRenderer` output, else the node's `text`, else extension placeholder plus `WARNING`. |
| Other macro with a body | Same header fallback, then the body blocks. This includes a bodied `pagetree` or `attachments`, which never expands. |
| `multiBodiedExtension` | Same header fallback, then every frame's body blocks. |
| Unknown node | Controlled by `unknownNodePolicy`. |

## Diagnostics

`Diagnostic` contains `code`, `message`, source `paths` as JSON Pointers, structured `details`, and an optional Java-only `cause`. `severity()` comes from the code, which fixes it. `MarkdownResult.diagnostics()` combines parse diagnostics with issues from content actually visited during rendering. `MarkdownResult.hasWarnings()` is true when any diagnostic is `WARNING`.

| Severity | Meaning | Example |
| --- | --- | --- |
| `INFO` | Non-lossy note. | Unknown node preserved as raw JSON. |
| `WARNING` | Content converted but changed or dropped. | Unsupported macro placeholdered. |
| `ERROR` | The operation failed. Empty output can also be a successful result. | Invalid JSON. |

Parse codes:

| Code | Severity | Raised when |
| --- | --- | --- |
| `INVALID_JSON` | `ERROR` | Payload is not valid JSON, exceeds the depth-100 nesting cap, or holds a number too large to read. |
| `INVALID_ROOT_TYPE` | `ERROR` | Root is not a JSON object. |
| `INVALID_ROOT_NODE` | `ERROR` | Root `type` is not `"doc"`. |
| `INVALID_VERSION` | `ERROR` | Root `version` is missing, fractional, or neither a number nor a numeric string. |
| `INVALID_CONTENT` | `ERROR` | Root `content` is not an array. |
| `UNSUPPORTED_VERSION` | `WARNING` | Root `version` is not `1`; parsing continues best effort. |
| `UNPRESERVED_JSON` | `WARNING` | Malformed JSON has no place in the AST and would be lost on write; rendering is unaffected. A `version` that isn't the integer it reads as, such as `"1"` or `1.5`, is one case. |

Any parse `ERROR` code means no document: `ParseResult.isSuccess()` is false and the body is empty. Blank input raises `EMPTY_INPUT`.

Conversion codes:

| Code | Severity | Raised when |
| --- | --- | --- |
| `UNKNOWN_NODE_PLACEHOLDER` | `WARNING` | Unknown nodes rendered as placeholders under `PLACEHOLDER`. |
| `UNKNOWN_NODE_SKIPPED` | `WARNING` | Unknown nodes dropped under `SKIP`. |
| `UNKNOWN_NODE_PRESERVED` | `INFO` | Unknown nodes preserved as raw JSON under `PRESERVE_RAW`. |
| `UNKNOWN_MARK_DROPPED` | `WARNING` | Unknown marks on text, media, paragraphs, or headings were dropped. |
| `UNSUPPORTED_MACRO` | `WARNING` | Extensions rendered as `[Extension: ...]` placeholders. |

Diagnostics include JSON Pointer `paths` and structured `details`; aggregated unknown-node and parser issues include a count. `UnknownNodePolicy.FAIL` returns `UNKNOWN_NODE_REJECTED`. `toMarkdown()` and explicitly throwing conveniences raise `AdfException` on failure.

## Lossy and by-design behaviours

These outcomes are expected for the selected options or for GFM limits. They do not set `hasWarnings()` unless the row says they raise a `WARNING`.

| Behaviour | Detail |
| --- | --- |
| Visual marks dropped | Color, background, border, font size, and alignment have no GFM equivalent unless `htmlVisualMarks` is enabled. Even then, a value outside the CSS allowlist stays dropped. |
| Table HTML fallback | Tables with spans, number columns, block-level cell content, or non-canonical headers use raw HTML. |
| `GFM_PROMOTE_FIRST_ROW` row promotion | Promotion is opt-in. The default `GFM_EMPTY_HEADER` synthesizes an empty header and keeps every original row as data. |
| Media and attachment placeholders | Without an attachment inventory carrying `downloadUrl`s or resolvers, media and attachments render to inert `media:` or `attachment:` destinations. A media node with no `id` renders to a bare `media` destination. |
| Unsupported macros | No built-in or custom renderer means the node's `text` when it has one, with no diagnostic. Otherwise a placeholder plus `WARNING`. A body still renders below the placeholder. |
| Dynamic page-list macros | `pagetree` and `children` need caller-supplied hierarchy. Without it they emit tokens and are recorded in `unresolved()`, and produce placeholder effects. |
| Excerpts | `excerpt` renders its body. `excerpt-include` needs `excerptResolver`; without it, the placeholder is recorded in `unresolved()` and produces a placeholder effect. |
| Charts | Bodied charts keep caption and body data. Modern charts keep caption only because their data table is a separate node. Legacy bodyless charts keep a label or a placeholder. |
| Migration macros | `inline-media-image` follows normal media rendering and contributes its file ID to metadata. |
| Adjacent lists | Two lists of the same Markdown kind that would merge are separated by an invisible `<!-- -->` comment, however they were produced. |
| Metadata scope | `ContentMetadata` reports what renders. A stray child the renderer ignores contributes nothing, and a macro with a body adds no reference of its own, except an `excerpt`, which is a definition. |
| Heading anchors | Explicit anchors are reserved first, so a generated slug never collides with one. `outline()` covers all rendered inlines, with a macro counted by its fallback label. |

## URL handling and safety

ADF input is untrusted. Link, card, media, and macro destinations are scheme-sanitized against this allow-list:

```text
http  https  mailto  tel  ftp  ftps  media  attachment
```

Unsafe schemes such as `javascript:`, `data:`, `vbscript:`, and `file:` have the scheme colon percent-encoded after control-character cleanup. The HTML table fallback also sanitizes URLs.

Caller-provided Markdown from `ExtensionRenderer` and `ExcerptResolver` is inserted verbatim. Escape or sanitize untrusted values before returning them.

`htmlVisualMarks` allowlists CSS values. A color or font size must be a hex color, a keyword, a number with an optional unit or `%`, or an `rgb()`, `rgba()`, `hsl()`, or `hsla()` call. A border needs a numeric size and an allowed color. Any other value drops that declaration and leaves the text in place, so ADF can't end a declaration or load a resource.

## CLI

```text
adf4j <command> [options] [<input-file>]
```

Input comes from `<input-file>` or stdin (also when the file is `-`) and must be UTF-8. Stdout contains only the requested output. Diagnostics and warnings go to stderr, except that `validate` reports its diagnostics as its stdout output. `-o` writes atomically. Help output honors the `NO_COLOR` environment variable.

| Command | Library method | Output |
| --- | --- | --- |
| `convert` | `convert(...)` | Markdown body by default, or full JSON with `-f json`. |
| `analyze` | `analyze(...)` | Metadata JSON by default, or text with `-f text`. |
| `validate` | `AdfValidation.validate(...)` | Schema conformance and diagnostics as text by default, or JSON with `-f json`. |

Root flags (before any command): `-h, --help` and `-V, --version`.

Flags shared by every command:

| Flag | Effect |
| --- | --- |
| `-h, --help` | Show the command's help. |
| `-v, --verbose` | Show library logs, and stack traces on error. Library logging is off without this flag. |
| `-q, --quiet` | Suppress stderr diagnostics, the summary line, and warnings. `error:` lines for failures that abort the command still print. |
| `-o, --output FILE` | Write output to `FILE` instead of stdout. |
| `--compact` | Single-line JSON instead of pretty. |

The CLI is built on [aesh](https://aeshell.github.io/), which also provides shell completion and generated docs: `adf4j --aesh-completion bash|zsh|fish|pwsh` prints a completion script (`--aesh-completion-install` installs it after a prompt), and `adf4j --aesh-doc markdown|asciidoc` prints full CLI documentation. Completion scripts call the command as `adf4j`, so the binary must be on the `PATH` under that name.

### convert

| Flag | Effect |
| --- | --- |
| `-f, --format md\|json` | `md`, the default, prints the body. `json` prints body, `hasWarnings`, diagnostics, metadata, and unresolved references. |
| `--fail-on-warning` | Exit 4 when warnings are present. |
| `--attachments FILE` | Supplies the attachment inventory. |
| `-t, --title TITLE` | Prepend a level-1 heading. |
| `-c, --collapse-hard-breaks` | Render hard breaks as soft breaks. |
| `-p, --escape-parentheses` | Escape literal `(` and `)`. |
| `--image-size` | Emit non-GFM image size attributes. |
| `--html-visual-marks` | Keep visual marks as inline HTML. |
| `--unknown-nodes V` | `placeholder`, `skip`, `fail`, or `preserve-raw`. |
| `--table-fallback V` | `gfm-promote-first-row`, `gfm-empty-header`, or `html`. |

`convert` also takes the [resolver flags](#resolver-flags).

### analyze

| Flag | Effect |
| --- | --- |
| `-f, --format json\|text` | Output format; `json` is the default. `text` carries the same data as one line per item. |
| `--select SECTIONS` | Comma-separated subset of `pageIds`, `externalUrls`, `mentionRefs`, `attachmentRefs`, `referencedFileIds`, `pageTreeRefs`, `excerptRefs`, `excerpts`, `outline`. Defaults to every section. |
| `--attachments FILE` | Supplies the attachment inventory, so attachment macros can contribute file IDs. |

`analyze` takes no rendering or resolver flags. It extracts metadata without rendering, so the inventory is its only input besides the document.

### validate

| Flag | Effect |
| --- | --- |
| `-f, --format text\|json` | Output format; `text` is the default. |
| `--fail-on-warning` | Exit 4 when a warning is present. |
| `--parse-only` | Check parseability without full schema validation. |

### Resolver flags

Resolver flags are accepted by `convert`; `--attachments` is accepted by `convert` and `analyze`. A `*-url` template substitutes placeholders after percent-encoding substituted values. A `*-map` file is a JSON lookup. If both are supplied, a map hit wins and the template is fallback. Unknown template placeholders are usage errors.

| Flag | Maps to | Schema |
| --- | --- | --- |
| `--media-url TPL` | `MediaResolver` | Template with `{id}`, `{collection}`, `{localId}`. |
| `--media-map FILE` | `MediaResolver` | `{ "<fileId>": "https://example.com/file" }` |
| `--attachment-url TPL` | `AttachmentResolver` | Template with `{fileId}`, `{title}`. |
| `--attachment-map FILE` | `AttachmentResolver` | `{ "<fileId>": "https://example.com/file" }` |
| `--page-url TPL` | `PageLinkResolver` | Template with `{pageId}`. |
| `--page-map FILE` | `PageLinkResolver` | `{ "<pageNodeId>": "https://example.com/page" }` |
| `--page-tree-map FILE` | `PageTreeResolver` | See below. |
| `--excerpt-map FILE` | `ExcerptResolver` | See below; values are verbatim. |
| `--attachments FILE` | `ConversionOptions.withAttachments(...)` | `[ { "fileId": "...", "title": "...", "mediaType": "...", "downloadUrl": "..." } ]` |
| `--extension-map FILE` | `ExtensionRenderer` | See below; values are verbatim. |

Absent entries decline the lookup. Present entries are answers, including empty page-tree arrays and empty excerpt Markdown. Blank values in URL maps decline and fall through to the template or placeholder. A supplied `--attachments` file, even `[]`, is authoritative, and only `fileId` is required in an entry. Unknown keys in `--attachments`, `--page-tree-map`, `--excerpt-map`, and `--extension-map` are usage errors.

`--page-tree-map`, where `<root>` is the macro's root, or `""` when it has none:

```json
{
  "pagetree": {
    "<root>": [{ "depth": 0, "title": "Overview", "pageNodeId": "123" }]
  },
  "children": { "<root>": [] }
}
```

`--excerpt-map`, where a `null` name selects the unnamed excerpt:

```json
[
  { "page": "DOCS:Onboarding", "name": "summary", "markdown": "..." },
  { "page": "Roadmap", "name": null, "markdown": "..." }
]
```

`--extension-map`, where `key` is required, `type` narrows the match when present, and each `{name}` token is filled from the macro's parameters, or left empty when the macro has no such parameter:

```json
[{ "type": "com.example", "key": "chart", "template": "![chart]({src})" }]
```

The CLI warns on stderr when `--excerpt-map` or `--extension-map` is used because their Markdown is not sanitized.

### JSON output

| Command | Top-level keys |
| --- | --- |
| `convert -f json` | `ok`, `body`, `hasWarnings`, `diagnostics`, `metadata`, `effects`, `unresolved`, `error?` |
| `analyze` | `ok`, `hasWarnings`, `diagnostics`, `metadata`, `error?`; `--select` filters metadata sections. |
| `validate -f json` | `ok`, `hasWarnings`, `diagnostics`, `error?` |

| Key | Value |
| --- | --- |
| `diagnostics[]` | `{ code, severity, message, paths, details }` |
| `pageIds`, `externalUrls`, `referencedFileIds` | Arrays of strings. |
| `mentionRefs[]` | `{ id, text }` |
| `attachmentRefs[]` | `{ fileId, title, mediaType, downloadUrl }` |
| `pageTreeRefs[]` | `{ macro, root, depth, all, parameters }`. `macro` is `pagetree` or `children`. `depth` appears only when the macro sets one. |
| `excerptRefs[]` | `{ page, excerptName, parameters }` |
| `excerpts[]` | `{ name }` describing source excerpts. |
| `outline[]` | `{ level, text, anchor }` |
| `unresolved` | `[{ kind, id, path, reason, fallback }]` |

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Success. |
| `1` | I/O error: a missing or unreadable input or map file, input that isn't UTF-8, or a failed write. |
| `2` | Usage error: unknown flag or command, invalid option value, unknown placeholder, or invalid map file. |
| `3` | Content failure: invalid input, meaning blank, malformed JSON, or no valid ADF root, or `--unknown-nodes fail`. |
| `4` | Quality gate: `--fail-on-warning`, `--fail-on-changes`, or `--fail-on-unresolved`. |
| `70` | Unexpected internal error. |

Every command exits 3 on invalid input and still writes its stdout: an empty body for `convert`, empty metadata for `analyze`, and the report for `validate`. A valid document with warnings exits 0 unless you add `--fail-on-warning`, `--fail-on-changes`, or `--fail-on-unresolved`.

## Distribution

Releases include GraalVM native executables for Linux, macOS, and Windows, plus a WebAssembly build.

The CLI jar is not a standalone fat jar. To run it on the JVM, put adf4j and its dependencies on the class or module path.

The WASM build exposes `convert(json, config?)`, `analyze(json, config?)`, and `version()`. Configuration has `markdown` and `context` sections; callbacks remain Java-only. CLI and WASM share their result projection. See the [2.0 migration guide](migration-2.md) for examples, quality gates, and the portable JSON Schemas.
