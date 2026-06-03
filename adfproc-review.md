# adf4j — Independent Review of Parsing/Rendering Correctness & Completeness

**Scope:** ADF → Markdown parsing and rendering in `adf4j-lib`, reviewed against the local spec snapshot (`docs/spec/adf-schema.json` draft-04, `docs/spec/structure.md`) and CommonMark/GFM semantics. Tests were treated as *unverified*: golden `.md` files and assertions were challenged, not assumed correct.

**Method:** full read of the parser, AST, renderer and supporting classes; manual walk of every node/mark fixture; and **empirical execution** of the real converter (`Adf.toMarkdown`) plus the embedded CommonMark renderer for the higher-risk claims. The existing suite is **green** as of this review (`./mvnw -o test` → BUILD SUCCESS). Findings below distinguish *verified by execution* from *verified by inspection*.

---

## Executive summary

The implementation is well-structured and, for the common cases, correct: inline escaping, code-fence sizing, adjacent-mark coalescing, mark ordering, the GFM/HTML table split, and unknown-node handling are all carefully done. Coverage of node/mark **types** is complete (every schema node and mark has a parser case and an exhaustive sealed dispatch in the renderer).

The weaknesses are concentrated in (a) a small number of **fidelity bugs** where real ADF shapes are mishandled, (b) **lossy-by-design** behaviors that are reasonable but undocumented/under-flagged, and (c) a **test methodology** that is essentially self-snapshotting — the golden `.md` files were evidently produced by the converter itself, so they lock in current behavior (including at least one real bug) rather than independently validating Markdown correctness.

### Priority table

| # | Severity | Area | Finding | Evidence |
|---|----------|------|---------|----------|
| P1 | **High** | Parser | Smart-link cards: title/URL read only from `attrs.url` + `data.title`; real JSON-LD `data.name`/`data.url` ignored → cards whose only payload is real `name`/`url` data collapse to `[Inline card]`/`[Card]`, dropping title **and** link | Executed |
| R1 | **Medium** | Render | Any table without an all-`tableHeader` first row (header-column tables, plain headerless tables) emits raw `<table>` HTML — a very common ADF shape | Executed |
| T1 | **Medium** | Tests | Golden `.md` are self-generated snapshots; card fixtures encode an invented `data.title` shape (and a schema-forbidden `data` attr on `embedCard`), so they *prove the P1 bug correct* | Inspection |
| R2 | Low-Med | Render | `expand`/`nestedExpand` `<summary>` title is injected unescaped → HTML injection / invalid `&` for titles with `< > &` | Executed |
| R3 | Low-Med | Render | Visual/structural marks silently dropped: `alignment`, `indentation`, `textColor`, `backgroundColor`, `fontSize`, `border` (alignment & indentation are semantically meaningful) | Inspection |
| R4 | Low | Render | Embedded `\n` inside a `text` node escapes leading block markers only at the node start → post-newline `# `/`- ` break out of the paragraph | Executed |
| R5 | Low | Render | File-media / `mediaInline` produce synthetic `media:<collection>/<id>` URLs that resolve nowhere and aren’t tied to attachment metadata | Executed |
| R6 | Low | Render | `embedCard` placed inline → `[Unsupported inline: embedCard]`, dropping its URL | Executed |
| R7 | Low | Render | A link whose text renders blank uses the **raw, unescaped href** as the label; an href containing `]`/`[` breaks the link so CommonMark emits literal text and the URL is lost | Executed |
| P2 | Low | Parser | `blockCard` with `datasource` (no url) renders `[Card: <id>]`; `mediaType`/`__file*` media attrs are product-specific extras not in schema | Inspection |
| Misc | Low/Info | Both | Panel→alert mapping lossy; `mediaGroup` images collapse onto one line; emoji-id-only / mention-no-text lose identity; `toPlainValue` drops array nulls | Inspection |

Two concerns I investigated and **cleared** (documented to save the next reviewer the trouble): the explicit-anchor heading (`<a id>` + single newline + `## H`) **does** render as a heading, not a swallowed HTML block; and the `[status](...)`/placeholder “accidental link” risk is **neutralized** by the unconditional escaping of `(` `)` `[` `]` in text nodes. See “Verified-correct” below.

---

## 1. Parsing findings

### P1 — Smart-link card `data` is read with the wrong field names *(High, executed)*

`AdfAstParser.parseCardAttrs` reads the url at `internal/parser/AdfAstParser.java:464` and the title at `:467`:

```java
var url = JsonFields.text(attrs, "url");                 // :464 — top-level only
var title = JsonFields.text(attrs.path("data"), "title"); // :467 — data.title
```

ADF smart links (`inlineCard`/`blockCard`/`embedCard`) carry an unresolved card as a **JSON-LD** object in `attrs.data`, where the human title is conventionally `name` and the link is `data.url` — not `title`. The schema confirms `data` is free-form `{}` (`inlineCard_node` / `blockCard_node`), and real exports use `name`/`url`.

Consequence (verified end-to-end): a card whose only payload is real `name`/`url` data —

```jsonc
{ "type":"inlineCard", "attrs":{ "data":{ "@type":"Object", "name":"Ticket 9", "url":"https://x/9" } } }
```

— renders as `[Inline card]`: both the title and the URL are lost (a `blockCard` of the same shape gives `[Card]`). Note the collapse is **conditional on the real shape** — the checked-in `data:{"title":…}` fixtures preserve the title precisely because they use the invented field name (see T1), which is what masks the bug. `[Embed card]` only arises from a `url`-less/`data`-less `embedCard` (the schema gives `embedCard` no `data` attr at all), not from this JSON-LD path.

**Fix:** when only `data` is present, resolve `url` from `attrs.url ?? data.url` and `title` from `data.name ?? data.title`. Add a real data-only fixture (see T1).

### P2 — Card URL not derived from `datasource`; product-specific media attrs *(Low, inspection)*

- A `blockCard` with a `datasource` but no `url` can only fall back to `[Card: <datasourceId|localId>]` (`internal/render/CardRenderer.java:16-23`). Acceptable, but worth documenting as “datasource cards are not linkified.”
- `parseMediaAttrs` reads `mediaType`, `__fileMimeType`, `__fileName` (`AdfAstParser.java:471-488`). These aren’t in the schema’s `media_node`; they’re Confluence/editor extras. Fine in practice, but undocumented — note that the parser intentionally accepts product extras beyond the published schema.

### P3 — `toPlainValue` drops nulls inside arrays *(Info, inspection)*

`AdfAstParser.toPlainValue` (`:509-543`) skips null children when copying arrays, which shifts indices and silently changes array shape in the generic `Attributes` view. Today nothing renders those arrays (e.g. `colwidth`), so this is latent — but if `Attributes` is ever surfaced to consumers it will misrepresent the source. Same “drop” logic in `parseMacroParams` is fine (macro params are a flat string map).

---

## 2. Rendering findings

### R1 — Headerless / header-column tables degrade to raw HTML *(Medium, executed)*

`TableRenderer.renderTable` (`:38`) takes the HTML path unless the **first row is entirely `tableHeader`** (`firstRowIsHeader`, `:120-129`). GFM genuinely cannot express a header column or a header-less table, so the fallback is defensible — but it fires for *extremely common* inputs. A trivial one-cell data table:

```
<table><tr><td>x</td></tr></table>
```

For a Markdown-targeted library this means a large fraction of real documents emit HTML blobs instead of Markdown tables. Consider an option to (a) promote the first row to a header, or (b) synthesize an empty header row (`| … |` + separator) so GFM is used whenever every cell is GFM-safe. At minimum, document the rule prominently. (The current `table-simple` fixture *is* a headerless table and its golden output is the HTML blob — see T1.)

### R2 — `expand`/`nestedExpand` title is not HTML-escaped *(Low-Medium, executed)*

`AdfRenderer.renderExpand` (`:395-403`) interpolates the raw title into `<summary>…</summary>`. A title `A <b>B</b> & C` yields `<summary>A <b>B</b> & C</summary>` — live HTML and an invalid `&`. Escape `<`, `>`, `&` (and ideally `"`) before insertion. Same applies anywhere the renderer emits raw HTML built from ADF strings (table cell HTML goes through jsoup `.text()`/`.html()` and is safer).

### R3 — Visual & structural marks are silently dropped *(Low-Medium, inspection)*

`TextMarkRenderer.isVisualOnlyHtmlMark` / `applyInlineMark` (`:92-115`) drop `textColor`, `backgroundColor`, `border`, `fontSize`; block-level `alignment` and `indentation` marks are also dropped (parsed, never rendered). The `*-omitted` fixtures show this is deliberate, and GFM has no equivalent for most of these. But **alignment** (center/right) and **indentation** carry real intent that vanishes with no trace. Recommendation: document the full drop-list in the public API, and consider an opt-in HTML fallback (`<div align>`, `<sub>`-style spans) mirroring the existing `imageSizeAttributes` opt-in.

### R4 — Leading-marker escaping misses newlines embedded in a `text` node *(Low, executed)*

`renderInlineNodes` (`AdfRenderer.java:188-206`) only sets `atLineStart` for the first inline and after a `HardBreak`. `escapeInlineText` neutralizes a leading block marker only at the string start. So a single text node `"intro\n# heading\n- item"` renders verbatim:

```
intro
# heading
- item
```

i.e. an `<h1>` and a bullet list break out of the paragraph. Well-formed ADF uses `hardBreak` nodes, so this is low-frequency, but importer-generated ADF can embed `\n`. Either normalize `\n` in text nodes (split into hardBreaks) or run leading-block neutralization on every output line, not just the first.

### R5 — Synthetic `media:` URLs are dead links *(Low, executed)*

`MediaRenderer.resolveMediaSource` (`:87-103`) emits `media:<collection>/<id>` for file media and `mediaInline` when no `url` is present (e.g. `![Local image](media:contentId-1/file-abc)`). These don’t resolve anywhere and aren’t connected to the attachment metadata the extractor already collects. Consider a media-resolver hook / base-URL option (parallel to the viewpdf `attachment:` handling) so file media can be turned into real links, and document the placeholder scheme.

### R6 — `embedCard` in inline position loses its URL *(Low, executed)*

`embedCard` is block-only in the schema, but editors do emit it inside paragraphs. `parseInline` has no case → `UnknownInline` → `[Unsupported inline: embedCard]` (fixture `inline/embed-card-as-inline-placeholder`), discarding the `url`. Cheap win: route inline cards/embeds through `CardRenderer` so at least the link survives.

### R7 — Blank-text link uses the raw href as an unescaped label *(Low, executed)*

`TextMarkRenderer.applyMarks:64` sets `label = href` when the rendered text is blank, then emits `[label](dest)` without escaping the label. A link mark applied to whitespace-only text whose href contains `]` (or `[`) breaks the link syntax:

```
href "https://e.com/x]y"  →  [https://e.com/x]y](https://e.com/x]y)
```

CommonMark parses that as literal text, not a link, so the URL is silently dropped — the same failure class as P1/R6, via a different path. The trigger is narrow (blank/whitespace link text plus a bracket in the href), but the fix is trivial: wrap the fallback label in `MarkdownText.escapeLinkText(...)` (it already escapes `[`/`]`).

### Misc rendering notes *(Low/Info)*

- **Panel → GFM alert** mapping (`AdfRenderer.gfmAlertType`, `:354-365`) is lossy: `info`/`note`/`custom` → NOTE, `success` → TIP, `error` → CAUTION. Reasonable, but `note` vs `info` and `success` semantics are flattened — document it.
- **`mediaGroup`** joins items with a single `\n` (`MediaRenderer.renderMediaGroup`, `:49-61`); a soft break collapses multiple images onto one rendered line. Likely intended for a “group,” but worth a comment/decision.
- **Emoji with only `id`** (no `text`/`shortName`) renders `""`; **mention with no `text`** renders `@mention`. Both lose identity that the `id` could partially preserve.
- **Status/decision/card placeholders** rely on the text-escaping of `(`/`[` to avoid accidental links (see Verified-correct). That’s robust today but brittle if escaping ever changes — a unit test pinning “placeholder immediately followed by `(`/`[`” would guard it.
- **Link/image URL schemes are not sanitized.** Hrefs are emitted verbatim and the table-cell HTML renderer is built with `sanitizeUrls(false)` (`AdfRenderer.markdownRenderingSupport`, `:110`), so a `javascript:`/`data:` href passes straight through. Defensible for a Markdown-targeted library (the output is Markdown, not trusted HTML, and sanitization is the consumer’s job) — but worth one explicit line in the public docs so downstream renderers know not to treat the output as pre-sanitized.

---

## 3. Verified-correct (investigated, *not* bugs)

- **Explicit-anchor heading.** `renderHeading` (`:287-289`) emits `<a id="…"></a>\n# Heading` (single newline). I worried the heading would be swallowed into an HTML block. Rendering through the bundled CommonMark engine confirms `<p><a id="x"></a></p>` + `<h2>Custom anchor</h2>` — the `<a>` is inline HTML (not a type-7 block, because the line doesn’t end right after the open tag), and the ATX heading correctly interrupts it. Works as intended.
- **Placeholder “accidental link.”** `[Blocked]` followed by text `(see ticket)` produces `[Blocked]\(see ticket\)` — the `(`/`)` are unconditionally escaped in text nodes (`MarkdownText.isInlinePunctuation`), so no link forms. Good defensive design.
- **Inline escaping suite** (leading `#`/`-`/`+`/`>`/ordered `1.`, indented-code via `&#32;`, backtick-run-aware fences, alt/URL-destination escaping, angle-wrapping URLs with spaces/unbalanced parens) is correct across the `escape-*`, `code-fence-collision`, and `link-*` fixtures.
- **Adjacent same-mark coalescing** (`coalesceAdjacentText`) correctly yields `**Hello**` / `` `ab` `` instead of split delimiters, and is order-insensitive on the mark set.
- **Type coverage is complete:** every schema node/inline/mark has a parser branch, and the renderer’s sealed-interface switches are exhaustive at compile time (incl. `UnknownBlock`/`UnknownInline`/`UnknownMark` with a configurable policy).

---

## 4. Test-suite critique (challenging the assumptions)

The suite has good *hygiene* (parameterized fixture discovery, a duplicate-payload guard, a json↔md pairing guard, unknown-node round-trip tests) but a weak *epistemology*:

### T1 — Golden files are self-snapshots that can encode bugs

`AdfSpecConversionTests` asserts `toMarkdown(input)` equals a checked-in `.md`. The `.md` files were evidently generated by running the converter (they reproduce implementation quirks verbatim: `media:` URLs, the em-dash slug, `data.title`). That makes them **regression guards, not correctness oracles** — a stable bug passes forever. Concretely:

- `inline/inline-card-data-only.json`, `inline/inline-card-with-title.json`, `nodes/embed-card-with-title.json` all use `data:{ "title": … }`. That shape is **not** what ADF emits (it’s `name`/`url`), and `embedCard` even has `additionalProperties:false` with no `data` property in the schema — so the fixture is schema-invalid. These golden files actively **certify the P1 bug as correct**, which is exactly the “don’t trust the tests” case.
- `nodes/table-simple.json` is a header-less table whose golden output is the `<table>` HTML blob — encoding R1 as intended behavior with no test exploring the GFM-friendly alternative.

**Recommendation:** validate card fixtures against real ADF JSON-LD samples; add an oracle that is independent of the converter — e.g. render the output through the bundled CommonMark/GFM parser and assert the resulting **structure** (headings are headings, links carry the expected href, no unintended `<h1>`), which would have caught R2 and R4 automatically.

### T2 — Coverage gaps (no fixture / no assertion)

No cases for: `textColor` mark; `underline` alone; `mediaSingle` `widthType:"pixel"`; `layoutColumn` widths; `breakout`/`fragment`/`dataConsumer` marks; `bodiedExtension` `excerpt` unwrap vs fallback; a GFM-eligible **headerless** table; a **header-column** table; a table cell containing a `hardBreak`; status/placeholder adjacent to `(`/`[`; a text node with an embedded `\n`; lists nested inside a panel / blockquote / table cell (the code even flags this as a “known limitation”); `mention.userType` / APP mentions; a non-numeric `date` timestamp; ordered list `order:0`.

### T3 — No negative / structural / fuzz assertions

Tests assert exact string equality only. There’s no check that output never contains accidental constructs, no round-trip structural check, and no property/fuzz testing over random ADF. A single “output parses to the intended node tree” property test would raise the floor substantially.

### T4 — Validation surface is thin

`AdfParsingService.validateRoot` checks root `type`/`version`/`content` shape only; child nodes are never schema-validated (by design — unknown nodes degrade gracefully). That’s a reasonable stance, but there’s no test asserting that a structurally-malformed child (e.g. `tableRow` with non-cell content, `listItem` with no paragraph) degrades sanely rather than throwing.

---

## 5. Prioritized recommendations

1. **Fix P1** (card `data.name`/`data.url`) and replace the invented `data.title` fixtures with real JSON-LD samples. Highest correctness-per-effort.
2. **Add an independent rendering oracle** (output → CommonMark parse → assert structure). This converts the snapshot suite into a real correctness check and catches R2/R4 and future regressions.
3. **Decide & document the table policy (R1):** offer header synthesis/promotion so common tables stay in GFM; keep HTML only for genuinely inexpressible tables.
4. **Escape HTML-significant chars** in `expand`/`nestedExpand` titles (R2) and audit any other raw-HTML string interpolation.
5. **Document the lossy marks (R3)** in the public API and consider an opt-in HTML fallback for alignment/indentation, mirroring `imageSizeAttributes`.
6. **Backfill the coverage gaps (T2)** and add the “known limitation” nested-list cases as explicit, asserted fixtures (passing or `@Disabled` with the expected target).
7. **Smaller wins:** escape the blank-text link label (R7, one-liner), media-resolver hook (R5), inline embed/blockCard linkification (R6), newline-normalization in text nodes (R4), emoji/mention identity fallbacks, and a docs note that output URLs aren’t scheme-sanitized.

---

*Reviewed against `docs/spec/adf-schema.json` (draft-04, fetched 2026-05-25) and CommonMark/GFM. Claims marked “executed” were reproduced by running `dev.nthings.adf4j.Adf.toMarkdown` and the bundled CommonMark renderer against the project’s compiled classes.*
