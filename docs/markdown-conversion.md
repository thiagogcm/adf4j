# Markdown conversion

`adf4j` converts Atlassian Document Format (ADF) JSON into Markdown, targeting
[GitHub-Flavored Markdown](https://github.github.com/gfm/) (GFM). The conversion is intentionally
**lossy by design**: ADF can express things GFM cannot (text colour, cell spans, panel variants),
and where there is no faithful Markdown form, `adf4j` either drops the styling, falls back to inline
HTML, or emits a readable placeholder. This document describes those behaviors so you can decide what
to configure and what to expect downstream.

## Entry points

There are two ways to convert:

```java
// Zero-config one-liner (shared, thread-safe instance, default options).
String md = Adf.toMarkdown(adfJson);

// Configurable: build a converter once and reuse it (immutable, thread-safe).
AdfToMarkdown converter = AdfToMarkdown.with(
    MarkdownOptions.defaults()
        .withTableFallback(TableFallback.GFM_PROMOTE_FIRST_ROW)
        .withMediaResolver(attrs -> "https://cdn.example.com/" + attrs.id()));

String body = converter.toMarkdown(adfJson);           // just the Markdown
MarkdownResult result = converter.convert(adfJson);    // body + metadata + diagnostics
```

`AdfToMarkdown.create()` is equivalent to `AdfToMarkdown.with(MarkdownOptions.defaults())`.
`Adf.toMarkdown(json)` uses `MarkdownOptions.defaults()` and is the right call when you do not need
to configure anything.

## Options

`MarkdownOptions` is an immutable record with `with…` copy methods. Pass `null` for any field and the
documented default applies.

| Option | Type | Default | Effect |
|---|---|---|---|
| `unknownNodePolicy` | `UnknownNodePolicy` | `PLACEHOLDER` | How unrecognised node/mark types are handled (see below). |
| `imageSizeAttributes` | `boolean` | `false` | When `true`, emits the **non-GFM** `{width= height=}` suffix on images. |
| `tableFallback` | `TableFallback` | `HTML` | How a GFM-safe table that lacks an all-header first row is rendered (see Tables). |
| `mediaResolver` | `MediaResolver` | `null` | Hook to turn file media (ids, no URL) into a real URL; `null` keeps the `media:` placeholder. |
| `context` | `ConfluenceRenderContext` | empty | Confluence render context (e.g. resolving `attachment:`/anchor links). |

`UnknownNodePolicy`:

| Value | Behaviour for an unrecognised node/mark type |
|---|---|
| `PLACEHOLDER` (default) | Emits `[Unsupported: <type>]` (block) / `[Unsupported inline: <type>]` (inline) and logs a warning. |
| `SKIP` | Drops the node, logs a warning. |
| `FAIL` | Throws `IllegalStateException`. |

## Security: output is Markdown, not sanitized HTML

> [!CAUTION]
> **Link and image URL schemes are emitted verbatim and are NOT sanitized.** A `javascript:` or
> `data:` href in the source ADF passes straight through to the output. The table-cell HTML renderer
> is deliberately built with URL sanitization disabled (`HtmlRenderer.sanitizeUrls(false)`).

The rationale is that the output is Markdown, and sanitization is the responsibility of the renderer
that ultimately turns that Markdown into HTML for display. **Do not treat `adf4j` output as
pre-sanitized.** If you render it to HTML in a security-sensitive context (e.g. a web page), run it
through your own sanitizer (URL-scheme allow-listing, HTML sanitization) just as you would for any
untrusted Markdown.

## Tables (`tableFallback`)

GFM tables require a header row (a row, then a `| --- |` separator). `adf4j` renders a table as a GFM
Markdown table **only** when its first row is composed entirely of `tableHeader` cells. Everything
else either falls back or is forced to HTML:

- **Header-column / header-less tables** (first row is not all-`tableHeader`): governed by
  `tableFallback`.
- **Always HTML, regardless of `tableFallback`:** a table with a number column
  (`numberColumnEnabled`), any cell with `colspan`/`rowspan` > 1, or any cell whose content is not
  GFM-expressible. Only `paragraph`, `media`, `mediaSingle`, and `mediaGroup` are considered
  GFM-safe cell content; a code block, a nested list, a panel, etc. in a cell forces the whole table
  to render as a raw `<table>` HTML blob.

`TableFallback` controls the header-less-but-GFM-safe case:

| Value | Effect |
|---|---|
| `HTML` (default) | Render the table as a raw `<table>` HTML blob. |
| `GFM_PROMOTE_FIRST_ROW` | Treat the first row as the header row. |
| `GFM_EMPTY_HEADER` | Prepend a synthesized empty header row, keeping every original row as data. |

Example — a header-less 2x2 data table:

`HTML` (default):

```html
<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>
```

`GFM_PROMOTE_FIRST_ROW`:

```markdown
| a   | b   |
| --- | --- |
| c   | d   |
```

`GFM_EMPTY_HEADER`:

```markdown
|     |     |
| --- | --- |
| a   | b   |
| c   | d   |
```

## Smart-link cards

`inlineCard`, `blockCard`, and `embedCard` render as Markdown links. An `embedCard` or `blockCard`
that appears **inline** (inside a paragraph) is treated as an inline card and is still linkified — its
URL survives.

URL and title are resolved from the card `attrs` (with JSON-LD fallbacks):

- **URL:** `attrs.url`, then the JSON-LD `attrs.data.url`.
- **Title:** the JSON-LD `attrs.data.name`.

The output depends on what is present:

| Has URL | Has title | Output |
|:---:|:---:|---|
| yes | yes | `[title](url)` |
| yes | no | `<url>` (or `[url](url)` if the URL is not clean) |
| no | yes | the title as plain text |
| no | no | `[Inline card]` / `[Card]` / `[Embed card]` (per kind) |

### Datasource-backed block cards (known gap, P2)

A `blockCard` backed only by a `datasource` (no URL) is **not linkified**. It renders as a label only:

```markdown
[Card: <datasourceId>]
```

falling back to the card's `localId` when there is no datasource id, and to `[Card]` when neither is
present. This is a known limitation: a datasource card carries no resolvable URL, so there is nothing
to link to.

## Media

ADF file/attachment media carries ids, not a URL. `adf4j` handles media as follows:

- **External media with a real `url`** is emitted as-is: `![alt](url)`.
- **File media (and `mediaInline`) without a `url`** emits a **synthetic placeholder**:
  `media:<collection>/<id>`, or `media:<id>` when there is no collection. This placeholder resolves
  nowhere on its own — it is a marker, not a working link.
- Supply a **`MediaResolver`** (`attrs -> url`) to turn file media into real URLs. The resolver wins
  over the placeholder; returning `null` or a blank string falls back to the `media:` placeholder.

```java
MarkdownOptions opts = MarkdownOptions.defaults()
    .withMediaResolver(attrs -> "https://cdn.example.com/file/" + attrs.id());
```

The alt text defaults to `media` when the source has none. A `mediaGroup` joins its items with a
single soft break (a newline) so the group renders as one visual cluster.

### Image size attributes (opt-in)

The `{width= height=}` suffix (e.g. `![alt](url){width=200 height=100}`) is **non-GFM** and is off by
default. Enable it with `withImageSizeAttributes(true)`. Only positive integer dimensions are
emitted.

## Marks: preserved, mapped, and dropped

Inline marks fall into three buckets. Several have **no GFM representation and are silently dropped**.

| Mark | Behaviour |
|---|---|
| `strong` | `**…**` |
| `em` | `*…*` |
| `strike` | `~~…~~` |
| `code` | `` `…` `` (inline code span; content is left literal, not escaped) |
| `link` | `[label](href)` / `[label](href "title")` |
| `subsup` | inline HTML `<sub>…</sub>` / `<sup>…</sup>` (preserved) |
| `underline` | inline HTML `<u>…</u>` (preserved) |
| `textColor` | **dropped** (no GFM form) |
| `backgroundColor` | **dropped** |
| `border` | **dropped** |
| `fontSize` | **dropped** |
| `alignment` (block) | **dropped** — note: alignment carries real intent that is lost |
| `indentation` (block) | **dropped** — note: indentation carries real intent that is lost |

The dropped marks still parse successfully; only their visual effect is lost. `alignment` and
`indentation` are called out because, unlike pure colour/size styling, they can change the meaning of
a document.

## Panels → GFM alerts (lossy mapping)

A `panel` maps to a GFM alert. Several distinct Atlassian panel types collapse onto the same alert
keyword, so the mapping is lossy:

| Panel type | GFM alert |
|---|---|
| `info`, `note`, `custom`, unknown / missing | `[!NOTE]` |
| `warning` | `[!WARNING]` |
| `error` | `[!CAUTION]` |
| `tip`, `success` | `[!TIP]` |

Note that GFM has no distinct "note" vs "info" alert and no "success" alert, so those semantic
distinctions are flattened.

## Other node behaviors

| Node | Behaviour |
|---|---|
| `date` | A numeric epoch-millis `timestamp` renders as an ISO `yyyy-mm-dd` date (UTC). A non-numeric timestamp is passed through verbatim; null/blank renders as empty. |
| `emoji` | Renders its `text`, then `shortName`. An emoji carrying only an opaque codepoint `id` (no text/shortName) renders as **empty** — there is no good Markdown form for a bare codepoint. |
| `mention` | Renders its display `text`; falls back to `@<id>` when the text is absent, then to a generic `@mention`. |
| `status` | Renders as `[text]` (e.g. `[Done]`); falls back to `[status]` when the text is blank. |
| `decisionItem` | Renders as a list item prefixed with `[decision]` / `[decision:<state>]`. |
| unknown node / mark types | Follow `unknownNodePolicy` (placeholder / skip / fail). |

## Known limitation: lists inside non-list blocks

A list nested **directly inside a non-list block** (a panel, a blockquote, or a table cell) is a
known limitation in indentation fidelity. Such a list re-enters rendering through the generic block
path and cannot receive the parent's marker-aligned indent, so it falls back to a fixed depth-based
indent. The list content is preserved; only the nesting indentation may not match a list nested
inside another list.

## Status placeholders & escaping

Placeholder text produced for `status`, `decision`, and cards (e.g. `[Done]`, `[decision]`,
`[Inline card]`) cannot accidentally form a Markdown link when it is immediately followed by `(` or
`[` in the surrounding text. In text nodes, inline punctuation — including `(`, `)`, `[`, and `]`
(alongside `` \ ` * _ ~ < & ``) — is **unconditionally backslash-escaped**. So a sequence like a
status `[Done]` followed by literal text `(see ticket)` cannot be misread as `[Done](see ticket)`:
the literal parentheses are escaped to `\(see ticket\)`.
