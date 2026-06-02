# adf4j — ADF→Markdown Converter Review

**Scope:** Systematic correctness/completeness review of the ADF→Markdown conversion logic in `adf4j-lib`, checked against the local ADF spec snapshot (`docs/spec/adf-schema.json`, `docs/spec/structure.md`) and the test fixtures under `adf4j-lib/src/test/resources/adf/spec`. **Date:** 2026-06-02 · **Reviewed at:** commit `a8cd005` (branch `main`).

## How findings were verified

The existing suite is green (`./mvnw -o test` passes). I did **not** assume the fixtures are correct. Every bug below was reproduced by compiling the library and running crafted inputs through the real public API (`dev.nthings.adf4j.Adf.toMarkdown`). Reproductions show **actual** output captured from the running converter, not predicted output.

## Headline

Type/mark **coverage is essentially complete** — every node and mark in the schema is parsed, and unknowns degrade gracefully (placeholder/skip/fail policy). The problems are in **rendering correctness**: the converter treats ADF text and identifiers as if they were already Markdown-safe. The single most important defect is that **literal text is never escaped**, which silently corrupts content and can fabricate structure (headings, lists, code blocks, emphasis, links) that was never in the source. Several block builders also emit Markdown that is invalid or lossy (code-fence collision, header-row-position-dependent tables, merged list paragraphs).

The fixture suite is clean and well-organized but **only exercises minimal happy-path inputs**, so none of these defects are currently caught by CI.

## Severity summary

| #   | Severity | Finding                                                                                            |
| --- | -------- | -------------------------------------------------------------------------------------------------- |
| 1   | **High** | Literal text is never escaped → markdown metacharacters corrupt content & invent structure         |
| 2   | **High** | Code block fence collision: embedded ` ``` ` breaks out of the block                               |
| 3   | **High** | Pipe table omits the delimiter row when the header row is not first → invalid table                |
| 4   | **High** | Multi-paragraph list items lose paragraph separation (merged into one paragraph)                   |
| 5   | Medium   | Nested-list indent is a fixed 2 spaces, ignoring ordered-marker width → under-indented sublists    |
| 6   | Medium   | Image `alt` and link/image URLs are not escaped/encoded → `]`, spaces, `()` break the output       |
| 7   | Medium   | Semantic marks dropped silently (subsup, underline …); a fixture enshrines `E=mc2` as canonical    |
| 8   | Low      | Card URL rendering is inconsistent (`[url](url)` vs `<url>`) and `data.title` is parsed but unused |
| 9   | Low      | Heading-with-inline-image glues image to text; anchor slug concatenates with no separator          |
| 10  | Low      | An `<a id>` is injected before **every** heading, duplicating GitHub's own auto-anchors            |
| 11  | Info     | GFM alert mapping loses nuance (`info→IMPORTANT`, `success→TIP`)                                   |
| 12  | Info     | Test suite covers only minimal cases; some fixtures encode lossy output as "correct"               |

---

## 1. (High) Literal text is never escaped

ADF stores text as **literal plain text**; all formatting is carried out-of-band in `marks`. A faithful Markdown emitter must therefore escape characters that Markdown would otherwise interpret. The converter does not. `AdfRenderer.renderText` (`AdfRenderer.java:310`) hands the raw string to `TextMarkRenderer.applyMarks`, which returns it unchanged when there are no marks (`TextMarkRenderer.java:31-34`). A `MarkdownText.escapeLinkText` helper exists (`MarkdownText.java:42`) but is only used for card link labels — body text, heading text, list text, table-cell text, captions, etc. all pass through verbatim.

**Reproductions (actual converter output):**

| ADF text (no marks)                             | Output                                      | Rendered as             |
| ----------------------------------------------- | ------------------------------------------- | ----------------------- |
| `Use *stars* and _unders_ and ` `` `ticks` `` ` | `Use *stars* and _unders_ and `` `ticks` `` | italic / italic / code  |
| `# Not a heading`                               | `# Not a heading`                           | **H1 heading**          |
| `- item`                                        | `- item`                                    | **bullet list**         |
| `1. item`                                       | `1. item`                                   | **ordered list**        |
| `> quote`                                       | `> quote`                                   | **blockquote**          |
| `    code-ish` (4 spaces)                       | `    code-ish`                              | **indented code block** |
| `a[b](c)`                                       | `a[b](c)`                                   | a **link**              |

The block-level cases are the most damaging: a paragraph whose text happens to start with `#`, `-`, `>`, `1.`, or four spaces is silently promoted into a different block type. Inline cases (`*`, `_`, `` ` ``, `[`, `<`) drop or transform characters.

This is not a stylistic choice — the code already escapes pipes in table cells (`TableRenderer.java:97`) and brackets in card labels, showing intent; the general case was just missed.

**Recommendation:** add a single `escapeText` routine and call it from `renderText` (and reuse for `alt`, captions, decision/TOC labels). A pragmatic CommonMark-style escaper covers backslash plus the inline set `` ` * _ [ ] ( ) ~ < `` always, and the line-start set `# > - + . )` and leading whitespace when the text begins a block. Take care **not** to escape inside code spans/blocks (there, fix the fence instead — see #2) and to escape _before_ marks/link wrapping are applied.

---

## 2. (High) Code block fence collision

`renderCodeBlock` (`AdfRenderer.java:283-287`) always fences with exactly three backticks (`"```" + language`). If the code body contains a ` ``` ` run, the first inner run terminates the block.

**Reproduction** — code block (language `md`) with body ` ``` \n nested \n ``` `:

````
```md
````

nested

```

```

````

A Markdown parser closes the block at the first inner ` ``` `; `nested` leaks out as prose and the trailing fence becomes an empty code block. CommonMark requires the opening fence to be **longer than any backtick run inside** the content.

**Recommendation:** compute the longest run of backticks in `codeBlock.text()` and use a fence of `max(3, longest+1)` backticks (and the matching closing fence). Tilde fences are an alternative.

---

## 3. (High) Pipe table drops the delimiter row unless the header row is first

In `renderTable` the GFM delimiter is emitted only inside the row loop, guarded by `if (row.header() && lines.size() == 1)` (`TableRenderer.java:77`). The table is routed to the pipe-table path whenever **any** row is all-headers (`hasHeaderRow`, line 34/116), but the delimiter is appended only when that header row happens to be the **first** rendered row.

**Reproduction** — a table whose first row is data and second row is the all-header row:

````

| d1 | d2 | | H1 | H2 |

```

There is **no `| --- |` delimiter line**, so this is not a valid GFM table — it renders as two lines of literal text. (The common header-first case works, which is why every fixture passes.)

**Recommendation:** decide the header row up front (first all-header row), render it first or emit the delimiter immediately after it regardless of position; if a header row exists but isn't first, prefer the existing HTML-table fallback (`renderHtmlTable`) rather than producing a delimiter-less pipe table.

---

## 4. (High) Multi-paragraph list items merge their paragraphs

`renderListItem` appends continuation blocks with no separating blank line (`ListRenderer.java:148-150` → `renderListItemBlock`, lines 155-178). A list item with two paragraphs therefore emits:

```

- First para Second para

```

In Markdown a single newline is a *soft* break, so this renders as one paragraph ("First para Second para"). To preserve two paragraphs a blank line is required between them.

This is also **internally inconsistent**: `renderBlockTaskItemLines` *does* insert the blank line (`ListRenderer.java:74-77`, `lines.add("")`), and the `block-task-item` fixture correctly shows:

```

- [ ] Main task

  with detail

```

So block-task-items and ordinary list items disagree on the same structure.

**Recommendation:** in `renderListItem`/`renderListItemBlock`, prepend a blank line before each subsequent block of an item (as block-task-items already do), keeping the continuation indented to the item's content column.

---

## 5. (Medium) Nested-list indentation ignores ordered-marker width

`LIST_INDENT` is a fixed two spaces (`RenderBuffer.java:10`) used for all nesting (`ListRenderer.java:126,172`). Two spaces is the content offset for a bullet (`- `), but an ordered marker is wider (`1. ` = 3, `10. ` = 4, `5. ` = 3), so sublists under ordered items are under-indented.

**Reproductions:**

```

1. one 10. ten
1. one-a 1. nested
1. one-b

```

Under CommonMark the child list must be indented to the parent's content column (≥3 for `1. `, ≥4 for `10. `). At 2 spaces these are non-conformant. Lenient renderers (GitHub) often tolerate it; strict CommonMark parsers will detach the sublist or re-interpret it. Deep/auto-numbered ordered lists are the realistic failure case.

**Recommendation:** indent children by the actual rendered marker width of the parent item, not a constant. (Bullets stay at 2; ordered items use `("" + number + ". ").length()`.)

---

## 6. (Medium) `alt` text and URLs are not escaped/encoded

- **Image alt** is interpolated raw into `![%s](...)` (`MediaRenderer.java:66`, `mediaDetails`
  87-96). An `alt` containing `]` breaks the image: alt `a] b` →
  `![a] b](https://e.com/x.png)` (the `]` closes the label early). `CardRenderer` escapes labels
  with `escapeLinkText`; `MediaRenderer` does not.
- **Link/image URLs** are inserted with no encoding. `TextMarkRenderer.applyMarks`
  (`TextMarkRenderer.java:61-69`) builds `[label](href)` from a raw href; a space or parenthesis
  breaks it: href `https://e.com/a b (c)` → `[link](https://e.com/a b (c))`, which most parsers
  mis-parse.

**Recommendation:** escape `[`/`]` (and ideally `(`/`)`) in `alt`; for destinations, wrap in
`<...>` when they contain spaces, or percent-encode spaces/parens/control chars. Apply uniformly to
link marks, media sources, cards, and embed URLs.

---

## 7. (Medium) Semantically meaningful marks are dropped silently — and a fixture blesses it

`isVisualOnlyHtmlMark` (`TextMarkRenderer.java:91-97`) drops `underline`, `subsup`, `textColor`,
`backgroundColor`, `border`, `fontSize`; `alignment`/`indentation` are likewise no-ops. Dropping
purely visual marks (colour, size) is reasonable for Markdown, but **sub/superscript and underline
change meaning**, and the fixtures enshrine the lossy result as canonical:

- `marks/subsup-omitted.md`: `H` + sub `2` + `O` → `H2O`; `E=mc` + sup `2` → `E=mc2`.
  `E=mc2` is simply wrong information, and `H2O` loses the subscript.

GFM has no native sub/sup, but `<sub>`/`<sup>` and `<u>` are widely rendered (incl. GitHub) and
would preserve meaning.

**Recommendation:** emit `<sub>…</sub>`/`<sup>…</sup>` (and optionally `<u>…</u>`) for these marks
and update the corresponding fixtures; keep dropping colour/size/border. At minimum, document the
loss explicitly rather than encoding `E=mc2` as the expected output.

---

## 8. (Low) Card rendering: inconsistent URL form, and `data.title` is ignored

- **Inconsistent autolink form.** `inlineCard`/`blockCard` with a URL render as `[url](url)`
  (`CardRenderer.renderInlineCard`/`renderCardUrl`, lines 26-53), but `embedCard` renders as
  `<url>` (line 41). Same concept, two encodings. Compare fixtures `inline/inline-card-url.md`
  (`[https://…](https://…)`) vs `nodes/embed-card.md` (`<https://…/video>`).
- **`data.title` parsed but unused.** `parseCardAttrs` extracts `data.title`
  (`AdfAstParser.java:463`), but `renderInlineCard`/`renderBlockCard` never use it — a data-only
  inline card always becomes the generic `[Inline card]` even when a human-readable title exists
  (`inline/inline-card-data-only.md`).

**Recommendation:** pick one bare-URL form (`<url>` autolink is the cleaner choice) and use it for
all three card kinds; fall back to `data.title` as the link label when no URL is present.

---

## 9. (Low) Heading containing an inline image

For `heading[ mediaInline, text ]`, `renderHeading` (`AdfRenderer.java:224`) emits the image glued
to the text, and `extractHeadingPlainText` (`AdfHeadingCollector.java:105-145`) concatenates the
image `alt` and the following text **with no separator** when building the anchor slug.

**Reproduction** — `H1[ image(alt "icon"), "Title" ]`:

```

<a id="icontitle"></a>

# ![icon](media:c/ic)Title

```

The slug `icontitle` fuses two words; the heading line glues image and text. (Confirmed against the
real `especificacoes-reporte-children` fixture, whose H1 mixes an inline image and text.)

**Recommendation:** when collapsing heading inlines to plain text for the slug, join distinct inline
sources with a space; consider dropping inline images from heading *display* (or at least keep a
space before following text).

---

## 10. (Low) An `<a id>` is injected before every heading

`AdfHeadingCollector.collect` always assigns an anchor (explicit, else a generated slug), so
`renderHeading` injects `<a id="…"></a>` before **every** non-empty heading
(`AdfRenderer.java:231-236`; see `nodes/heading-all-levels.md`). Because the slug algorithm matches
GitHub's own heading-anchor generation, the output then carries two anchors with the same id (the
injected `<a id="flow">` and GitHub's implicit `#flow`), which is invalid HTML (duplicate id) and
visually noisy. Explicit Confluence anchors and TOC targets genuinely need an injected anchor;
plain headings arguably do not.

**Recommendation:** inject `<a id>` only for headings that carry an **explicit** anchor (or are TOC
targets), and rely on the renderer's own slugging otherwise — or make injection an opt-in option.

---

## 11. (Info) GFM alert mapping loses nuance

`gfmAlertType` (`AdfRenderer.java:262-273`) maps `info→IMPORTANT`, `note→NOTE`, `warning→WARNING`,
`error→CAUTION`, `tip/success→TIP`, default→`NOTE`. Reasonable overall, but `info→IMPORTANT`
over-escalates (info panels are usually informational, i.e. NOTE), and `success→TIP` discards the
success semantic. `panelType: custom` collapses to NOTE, dropping any `panelIcon`/`panelColor`.
Defensible, but worth a deliberate decision/doc.

---

## 12. (Info) Test-suite critique

The suite is hygienic — `AdfSpecConversionTests` auto-discovers JSON/MD pairs, asserts every input
has an expected output, and forbids duplicate payloads. But:

- **Coverage is happy-path only.** Every defect above (#1–#6) is absent from the fixtures: there is
  no fixture with markdown metacharacters in text, no code block containing backticks, no
  header-not-first table, no multi-paragraph ordinary list item, no deep/auto-numbered nested
  ordered list, no `alt`/URL with special characters. The green suite therefore provides false
  confidence on exactly the cases that break.
- **Some fixtures encode lossy/debatable output as canonical**, which will actively resist fixes:
  - `marks/subsup-omitted.md` → `E=mc2` / `H2O` (see #7).
  - `nodes/media-group.md` joins two images with a single newline, so they render on one line
    (soft break) rather than stacked — fine for a gallery, but undocumented and surprising.
  - `nodes/table-simple.md` routes a header-less table to a full HTML `<table>`; reasonable, but a
    deliberate design choice that should be stated.
  - `inline/inline-card-url.md` vs `nodes/embed-card.md` bless the two inconsistent URL forms in #8.
- **`embed-card-as-inline-placeholder`** asserts that a block card placed inline becomes
  `[Unsupported inline: embedCard]`. That is acceptable handling of invalid ADF, but note the
  asymmetry: `inlineCard` is handled in inline position while `blockCard`/`embedCard` are not.

**Recommendation:** add an adversarial fixture group (escaping, fence collision, table-header
position, loose list items, deep ordered nesting, special chars in `alt`/URLs). Fixing #1–#6 will
require updating these adversarial expectations and a few of the lossy fixtures above.

---

## Coverage notes (the good news)

- **Node coverage** matches the schema: every `doc`-level, child, and inline node in
  `adf-schema.json` has a parser case (`AdfAstParser.parseBlock`/`parseInline`), including
  `syncBlock`/`bodiedSyncBlock`, `layoutSection`, `nestedExpand`, `bodiedExtension`, cards, and
  media. `multiBodiedExtension`/`extensionFrame` (named only in `structure.md`, absent from the
  schema) fall through to the Unknown-node policy — acceptable.
- **Mark coverage** is complete (all 17 schema marks parsed; unknown marks preserved as
  `UnknownMark` and skipped).
- **Robustness**: invalid roots, blank input, and malformed JSON are handled with structured
  diagnostics; unknown nodes honor SKIP/PLACEHOLDER/FAIL; parsing is null-tolerant throughout.
- **Confluence extras** (viewpdf attachment resolution, anchor macros, TOC, children, internal page
  link detection, attachment/external/page metadata dedup) are coherent and well-tested.

## Suggested priority order

1. **#1 text escaping** — highest impact, affects every text-bearing node; design the escaper once
   and reuse it (also fixes the `alt` half of #6).
2. **#2 code fence** and **#3 table delimiter** — localized, high-impact correctness fixes.
3. **#4 list-item paragraphs** and **#5 ordered-list indent** — list fidelity; #4 also removes an
   internal inconsistency with block-task-items.
4. **#6 URL encoding**, then the Low/Info items (#7–#11) as deliberate product decisions.
5. Backfill the adversarial fixtures (#12) alongside each fix so the regressions are locked in.
```
