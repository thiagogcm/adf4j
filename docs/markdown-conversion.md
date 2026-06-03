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
| `tableFallback` | `TableFallback` | `GFM_PROMOTE_FIRST_ROW` | How a GFM-safe table that lacks an all-header first row is rendered (see Tables). |
| `htmlVisualMarks` | `boolean` | `false` | When `true`, preserves the visual-only marks (`textColor`/`backgroundColor`/`border`/`fontSize`) as an inline `<span style="…">` instead of dropping them. |
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
| `GFM_PROMOTE_FIRST_ROW` (default) | Treat the first row as the header row. |
| `GFM_EMPTY_HEADER` | Prepend a synthesized empty header row, keeping every original row as data. |
| `HTML` | Render the table as a raw `<table>` HTML blob. |

Example — a header-less 2x2 data table:

`GFM_PROMOTE_FIRST_ROW` (default):

```markdown
| a   | b   |
| --- | --- |
| c   | d   |
```

`HTML`:

```html
<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>
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
| yes | no | `<url>` when the URL is an absolute URI (has a scheme) and clean; otherwise `[url](url)` — so a relative/scheme-less link (e.g. `/wiki/x/123`) stays a working link instead of an invalid autolink |
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

- **External media with a real `url`** is emitted as-is.
- **File media (and `mediaInline`) without a `url`** emits a **synthetic placeholder**:
  `media:<collection>/<id>`, or `media:<id>` when there is no collection. This placeholder resolves
  nowhere on its own — it is a marker, not a working link.
- Supply a **`MediaResolver`** (`attrs -> url`) to turn file media into real URLs. The resolver wins
  over the placeholder; returning `null` or a blank string falls back to the `media:` placeholder.

**Images vs. other attachments.** ADF `media` covers arbitrary files (PDFs, video, Office docs,
archives), not just images. An image embed (`![alt](src)`) only makes sense for an image, so the
media kind is classified from its MIME/`mediaType` (then its filename extension):

- **Image** (or unknown kind) → an image embed `![alt](src)`.
- **Non-image** (e.g. `application/pdf`, a `.zip`) → a plain link `[name](src)` (the `name`/filename
  becomes the label), so the attachment is usable instead of a broken image. The opt-in `{width=
  height=}` size suffix is never added to a non-image link.

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
| `code` | `` `…` `` (inline code span; content is left literal, not escaped — a newline inside it collapses to a single space, as CommonMark code spans cannot contain a line break) |
| `link` | `[label](href)` / `[label](href "title")` |
| `subsup` | inline HTML `<sub>…</sub>` / `<sup>…</sup>` (preserved) |
| `underline` | inline HTML `<u>…</u>` (preserved) |
| `textColor` | **dropped** by default; `<span style="color:…">…</span>` when `htmlVisualMarks` is on |
| `backgroundColor` | **dropped** by default; `<span style="background-color:…">` when `htmlVisualMarks` is on |
| `border` | **dropped** by default; `<span style="border:…">` when `htmlVisualMarks` is on |
| `fontSize` | **dropped** by default; `<span style="font-size:…">` when `htmlVisualMarks` is on |
| `indentation` (block) | preserved as a run of non-breaking spaces on the paragraph/heading (see below) |
| `alignment` (block) | **dropped** — note: alignment carries real intent that is lost |

The dropped marks still parse successfully; only their visual effect is lost. When `htmlVisualMarks`
is enabled, the four visual marks on a text run are combined into a single `<span style="…">` (the
style values are escaped to stay inside the attribute). `alignment` is called out because, unlike
pure colour/size styling, it can change the meaning of a document and has no faithful GFM form.

`indentation` (a paragraph/heading block mark, level 1–6) is preserved by prefixing the block with a
run of non-breaking spaces (U+00A0) per level. Ordinary leading spaces are unreliable in Markdown (4+
become an indented code block; ≤3 can still front a block marker), whereas a non-breaking space is
plain text that renders as visible indentation without changing the parse.

## Panels → GFM alerts (lossy mapping)

A `panel` maps to a GFM alert. Several distinct Atlassian panel types collapse onto the same alert
keyword, so the mapping is lossy:

| Panel type | GFM alert |
|---|---|
| `info`, `custom`, unknown / missing | `[!NOTE]` |
| `note` | `[!NOTE]` + a `> **Note**` label line |
| `warning` | `[!WARNING]` |
| `error` | `[!CAUTION]` |
| `tip` | `[!TIP]` |
| `success` | `[!TIP]` + a `> **Success**` label line |

GFM has only five alert kinds, with no distinct "note" vs "info" and no "success". To avoid flattening
those away, the two types that would otherwise be indistinguishable from another (`note` collapsing
into `info`'s `NOTE`, `success` into `tip`'s `TIP`) keep a bolded label as the first line inside the
alert. The types whose name already matches their alert get no extra label.

## Other node behaviors

| Node | Behaviour |
|---|---|
| `date` | A numeric epoch-millis `timestamp` renders as an ISO `yyyy-mm-dd` date (UTC). A non-numeric timestamp is passed through verbatim; null/blank renders as empty. |
| `emoji` | Renders its `text`, then `shortName`. An emoji carrying only an opaque codepoint `id` (no text/shortName) renders as **empty** — there is no good Markdown form for a bare codepoint. |
| `mention` | Renders its display `text`; falls back to a neutral `@unknown` when the text is absent (the opaque ARI/UUID id is not surfaced). |
| `status` | Renders as `[text]` (e.g. `[Done]`); falls back to `[status]` when the text is blank. |
| `decisionItem` | Renders as a list item prefixed with `[decision]` / `[decision:<state>]`. |
| unknown node / mark types | Follow `unknownNodePolicy` (placeholder / skip / fail). |

## Table of contents and heading anchors

A Confluence `toc` macro renders as a nested bullet list of links to the document's headings, each
targeting the heading's slug (`#overview`). The slug is generated by commonmark's id generator (the
same algorithm GitHub uses for ASCII headings), including duplicate-name suffixing (`overview`,
`overview-1`, …).

To make those links resolve on **any** renderer — not just one whose heading slugger happens to match
commonmark's — every heading a `toc` references gets a self-contained `<a id="…"></a>` anchor injected
on the line above it, using that same slug. So the link and its target always agree, even on consumers
that slug Unicode/punctuation differently or do not auto-assign heading ids at all. Headings carrying
an explicit Confluence `anchor` macro are likewise anchored; headings in a document with no `toc` are
left bare (no injected anchor).

A list nested **directly inside a non-list block** (a panel, a blockquote, or a table cell) is a
known limitation in indentation fidelity. Such a list re-enters rendering through the generic block
path and cannot receive the parent's marker-aligned indent, so it falls back to a fixed depth-based
indent. The list content is preserved; only the nesting indentation may not match a list nested
inside another list.

## Escaping

**Every attribute-derived string is escaped, not just text nodes.** Inline punctuation — `(`, `)`,
`[`, `]`, alongside `` \ ` * ~ < & `` — is backslash-escaped in the output of `text`, `placeholder`,
`mention`, `emoji`, `date`, and an extension's fallback `text`. So none of them can inject a link or
emphasis into the document (e.g. a placeholder whose text is `[label](http://evil)` renders as the
literal `\[label\]\(http://evil\)`, not a live link).

When one of these inlines is the **first thing on a line** (a paragraph, a list item's or caption's
first paragraph, a block extension's fallback text), a leading block marker (`#`, `-`, `+`, `>`, or
an ordered `1.`) is also neutralised, so the consumer cannot promote it to a heading, list, or quote.

**Bracket-label tokens are self-escaped.** The literal `[…]` placeholders for `status` (`\[Done\]`),
`decision` (`\[decision\]`), cards (`\[Inline card\]`), sync blocks, extensions, PDF, and chart
macros have their brackets escaped, so they render identically (`[Done]`) but can never be misread as
a reference/inline link — regardless of what follows them.

`_` is escaped the same way **except** when it sits between word characters (e.g. the `_` in
`snake_case`): CommonMark never treats an intra-word `_` as emphasis, so escaping it would be
pointless noise and it is left literal. A `_` at a word boundary (e.g. `_word_`) is still escaped, as
it could otherwise open or close emphasis.
