# adf4j — ADF → Markdown Processing Review

**Date:** 2026-06-02

**Scope:** Systematic review of the ADF→Markdown converter (`adf4j-lib`) against the local ADF spec (`docs/spec/adf-schema.json`, `structure.md`), plus a critical analysis of every test fixture and the JUnit suites. The brief was explicitly *not* to assume the tests are correct, but to challenge their assumptions and judge whether the processing is correct and complete.

**Method:** Read the full schema and all converter source; cross-referenced node/mark/attr coverage against the schema; read all 60+ `.json`/`.md` fixture pairs and every JUnit test. Behavior was then confirmed empirically rather than by inspection: the project was built (Java 25) and the real `Adf.toMarkdown` was run against ~30 adversarial inputs, and every "renders as…" claim was settled by rendering the converter's Markdown to HTML through the bundled CommonMark (the converter's own downstream). The existing suite is green (138 tests, incl. 90 auto-discovered conversion pairs). Every "Evidence" block below is actual converter output captured from those runs.

---

## TL;DR

The converter is well-structured and its **type coverage is complete** — every node and mark type in the schema is parsed and rendered, and the escaping/table/list machinery is unusually careful. The problems are in **edge behavior that the fixtures don't exercise**, and a few fixtures **enshrine output that is wrong on the most likely target (GitHub-flavored Markdown)**.

Confirmed correctness bugs (silently produce wrong or broken output for valid input). No finding rises to release-blocking/High; the real bugs are all **Medium** or **Low**. The `#` column is the detailed Finding number in §2.

| # | Severity | Issue |
|---|----------|-------|
| 1 | **Medium** | Adjacent text nodes sharing a `strong`/`em`/`strike`/`code` mark are not coalesced, producing spans with visible stray delimiters (`**a****b**` → `<strong>a****b</strong>`). |
| 2 | **Medium** | Literal text shaped like an HTML entity (`&amp;`, `&lt;`, `&#39;`) is not escaped and is silently decoded by any CommonMark→HTML consumer. |
| 3 | **Medium** | A `codeBlock` inside a GFM table cell is flattened with `<br>`, emitting a literal ```` ```java<br>… ```` — the fenced block is destroyed. (`taskList`/`decisionList`/`rule`/`heading`/`panel`/`blockquote` in a cell degrade the same way.) |
| 4 | **Medium** | A `hardBreak` inside a `heading` truncates the heading and leaks the rest into a new paragraph (`## a␣␣⏎b` → `<h2>a</h2><p>b</p>`), and desyncs the heading from its outline slug/anchor. |
| 5 | **Medium** | Image size suffix `{width=W height=H}` is **not** GFM — on GitHub it shows as literal text after the image. (Enshrined by a passing fixture.) |
| 6 | **Low** | A link/card URL with unbalanced parens and no spaces truncates (close-paren) or entirely loses (open-paren) the link. |
| 7 | **Low** | TOC macro link labels are not escaped (the heading body is), so unbalanced brackets in a heading break the TOC link. |

Completeness gaps (lossy, not broken): `mediaSingle`-level `link` mark dropped; extension `text` fallback ignored.

Test-suite gaps: the **TOC macro is never asserted end-to-end**; no fixture covers entities, non-leaf blocks in table cells, hardBreaks in headings, adjacent same-mark runs, task lists in list items, or media links; and the image-attribute fixture encodes a non-portable convention as "correct".

---

## 1. Coverage assessment (completeness of *type* handling)

Cross-referencing `AdfAstParser` against the schema's `definitions`:

- **Block nodes** — all 33 schema block/child node types are handled in `parseBlock` (`AdfAstParser.java:112-155`): `doc, paragraph, heading, blockquote, codeBlock, panel, rule, bulletList, orderedList, listItem, taskList, taskItem, blockTaskItem, decisionList, decisionItem, table, tableRow, tableCell, tableHeader, mediaSingle, mediaGroup, media, caption, expand, nestedExpand, layoutSection, layoutColumn, extension, bodiedExtension, syncBlock, bodiedSyncBlock, blockCard, embedCard`. Unrecognized types → `UnknownBlock` (policy-driven).
- **Inline nodes** — all 10 inline types handled in `parseInline` (`:171-200`): `text, hardBreak, inlineCard, mediaInline, date, emoji, mention, placeholder, status, inlineExtension`. (The block-only `embedCard`/`blockCard` are *not* inline cases — see §4.)
- **Marks** — all 17 schema marks handled in `parseMark` (`:216-242`): `strong, em, code, strike, underline, subsup, link, textColor, backgroundColor, alignment, indentation, fontSize, border, annotation, breakout, fragment, dataConsumer`. Unrecognized → `UnknownMark`.

So there are **no missing node/mark types**. The completeness problems are about *attributes and content models* within handled types (Findings 3, 8, 9 and the §4 items), not about whole categories being absent.

---

## 2. Correctness bugs

### Finding 1 — Adjacent same-mark text nodes are not coalesced, corrupting the span · **Medium**

Marks are applied per text node (`AdfRenderer.renderText` → `TextMarkRenderer.applyMarks`); there is no coalescing of adjacent text nodes that share the same mark. Each run gets its own delimiters, and the concatenated delimiters then mis-parse. This corrupts every common inline mark, not just `code` — each row below is the converter's actual output and its HTML render through the bundled CommonMark:

| Two adjacent runs `a`,`b` with mark | Converter output | Renders to |
|---|---|---|
| `code`   | `` `a``b` ``   | `<code>a``b</code>` (merged span, stray backticks) |
| `strong` | `**a****b**`   | `<strong>a****b</strong>` (four literal `*`) |
| `em`     | `*a**b*`       | `<em>a**b</em>` (two literal `*`) |
| `strike` | `~~a~~~~b~~`   | `<del>a~~~~b</del>` (four literal `~`) |

Three adjacent `strong` runs are messier still: `**x****y****z**` → `<strong>x<strong><strong>y</strong></strong>z</strong>`. ADF normally merges same-mark runs, but splits do occur (e.g. a differing `link`/`annotation` on part of the run, or markdown/HTML imports), so this is reachable with valid input.

**Fix:** coalesce consecutive text nodes that carry **identical mark sets** before applying marks (for all marks, not just `code`), then apply the mark once and size any code fence to the merged content.

---

### Finding 2 — HTML-entity-shaped literal text is not escaped (silent corruption) · **Medium**

`MarkdownText.isInlinePunctuation` (`MarkdownText.java:100-105`) escapes `` \ ` * _ [ ] ( ) ~ < `` but **not `&`**. CommonMark resolves valid entity/numeric references in ordinary text, so any literal `&word;` / `&#nn;` in the source is decoded by a downstream renderer.

**Evidence** (input text is the literal string `AT&amp;T, 5 &lt; 6, R&D, &#39;q&#39;`):
```
AT&amp;T, 5 &lt; 6, R&D, &#39;q&#39;
```
The Markdown is unchanged, but a CommonMark→HTML pass emits `AT&amp;T…` whose *browser rendering* is `AT&T, 5 < 6, R&D, 'q'` — i.e. the reader sees altered text. (Bare `&` like `R&D` is safe; only entity-shaped runs `&word;` / `&#nn;` corrupt.)

This is doubly inconsistent because the converter **itself relies on entity decoding**: it emits `&#32;` to defeat indented-code promotion (`MarkdownText.java:113-120`). So it assumes entities are decoded on output, yet does not protect the user's own entity-shaped text from that same decoding.

**Fix:** add `&` to the escaped set (escape as `\&`), or specifically escape `&` that begins a valid entity/numeric reference.

---

### Finding 3 — `codeBlock` (and other non-leaf blocks) inside a GFM table cell are destroyed · **Medium**

`TableRenderer.requiresHtmlTableFallback` (`TableRenderer.java:132-146`) only forces the HTML-table fallback for **colspan/rowspan** or a cell containing a **bulletList/orderedList**. But the schema's `table_cell_content` (`adf-schema.json:3024-3091`) also allows `codeBlock, heading, panel, blockquote, rule, taskList, decisionList, mediaSingle, mediaGroup, nestedExpand, blockCard, embedCard, extension`. When the table has a header row (→ GFM path), those blocks go through `renderTableCell` (`:90-98`), which does `.joinBlocks(...).replace("\n","<br>")`. For a code block this turns the fence into literal text.

**Evidence** (header-row table whose first body cell is a `codeBlock`):
```
| H1                                         | H2  |
| ------------------------------------------ | --- |
| ```java<br>int x = 1;<br>int y = 2;<br>``` | ok  |
```
The ```` ``` ```` fences are now literal characters inside the cell; on GitHub this renders as the text "```java" … "```", not a code block. The code's structure is lost. The same flattening degrades every other non-leaf block in a cell (all reproduced + HTML-confirmed): `heading` → literal `## Title`; `panel` → `> [!NOTE]<br>> note`; `blockquote` → `> quote`; `taskList` → `- [ ] t` (renders as literal text, **not** a checkbox); `decisionList` → `- [decision:DECIDED] d`; `rule` → literal `---`. (`codeBlock` is the only outright break; the rest are readable-but-wrong literal text.)

**Severity note:** real corruption, but the trigger is narrow — a non-leaf block specifically inside a header-row table cell. The representative `reporte`/Open-Finance fixtures do not hit it (their lone `codeBlock` sits directly under `doc`, never in a cell), so it is rated Medium on impact, not frequency.

**Fix:** broaden `requiresHtmlTableFallback` to also force HTML when any cell contains a block that a GFM cell can't represent inline — i.e. anything other than a `Paragraph` (or a single image-bearing `MediaSingle`/`Media`, which renders fine inline and need not trigger the fallback). Concretely add `CodeBlock, Heading, Panel, Blockquote, Rule, TaskList, DecisionList, NestedExpand`, nested `Table`, and the card/extension blocks to the trigger. Paragraph- and image-only cells stay on the GFM path.

---

### Finding 4 — A `hardBreak` inside a `heading` truncates the heading · **Medium**

A `heading`'s content is `inline_node`, which includes `hardBreak` (`adf-schema.json:1569-1574` → `inline_node` at `:1666-1702`), so a multi-line heading is schema-valid. `AdfHeadingCollector` normalizes only *leading/trailing* hardBreaks (`AdfHeadingCollector.java:82-103`); an interior one survives, and `AdfRenderer.hardBreakMarker` (`AdfRenderer.java:378-380`) emits `"  \n"` (no heading-context guard). An ATX heading is single-line, so the newline ends it.

**Evidence** (heading = text `a`, `hardBreak`, text `b`; output then rendered to HTML):
```
MD:    ## a␣␣⏎b
HTML:  <h2>a</h2>
       <p>b</p>
```
The heading is truncated to "a" and "b" leaks out as a sibling paragraph — clear structural corruption. It also **desyncs the outline**: `extractHeadingPlainText` joins the hardBreak as a space (`AdfHeadingCollector.java:115-116`), so the outline/`HeadingReference` slug is built from "a b" (→ `#a-b`) while the rendered heading is only "a" (GitHub slug `#a`) — any TOC link to that heading points at the wrong anchor.

**Fix:** in heading context, render an interior `hardBreak` as a space (or drop it) instead of `"  \n"`, matching how the outline already collapses it.

---

### Finding 5 — Image size suffix `{width= height=}` is not GFM · **Medium**

`MediaRenderer.renderImageAttributeSuffix` (`MediaRenderer.java:102-114`) appends `{width=W height=H}` after the image. This is **commonmark-java's ImageAttributesExtension / Pandoc** syntax — it is not GitHub-flavored Markdown. The rest of the output deliberately targets GFM (alerts, tables, task lists), so this is inconsistent: on GitHub the suffix renders as the literal text `{width=800 height=400}` after the image.

**Evidence** — this is exactly what `nodes/media-single-external.md` asserts as correct:
```
![Sized](https://example.com/diagram.png){width=800 height=400}
```

**Challenge to the test:** the fixture encodes a non-portable convention as the expected result. On the most likely consumer (GitHub) it is visible junk. Either (a) drop the suffix, (b) emit an HTML `<img>` when a size is present (portable, but trades Markdown purity for an HTML element — note the `reporte` regression guard at `AdfToMarkdownRenderingTests:47-48` forbids `<img>` in that case), or (c) make it an opt-in via `MarkdownOptions` and document the required Markdown flavor. Whichever is chosen, the decision and the target flavor should be explicit.

---

### Finding 6 — URL with unbalanced parentheses (no spaces) breaks the link · **Low**

`MarkdownText.escapeUrlDestination` (`MarkdownText.java:162-178`) only angle-wraps a destination when it contains a space or control char. A URL with unbalanced `(`/`)` and no space is emitted bare inside `(...)`; CommonMark stops the destination at the first unbalanced `)`. This affects both text-node links and smart-link cards (`CardRenderer.java:51`).

**Evidence** — the close-paren case truncates; the open-paren case is *worse* and loses the link entirely (both reproduced + HTML-confirmed):
```
href https://e.com/a)b  →  [x](https://e.com/a)b)      → links only to .../a , "b)" trails
href https://e.com/a(b  →  [x](https://e.com/a(b)      → <p>[x](https://e.com/a(b)</p>  (no link at all)
```
(Balanced parens such as `…/Foo_(disambiguation)` are fine; the link-href-with-space fixture works because of the space path.)

**Fix:** also angle-wrap (or percent-encode) when the destination's parens are unbalanced.

---

### Finding 7 — TOC macro link labels are not escaped · **Low**

`MacroRenderer.renderTocMacro` (`MacroRenderer.java:125-132`) builds `- [<heading.text()>](#anchor)` using the **raw** heading text. The heading *body* is escaped, and link labels built from text nodes are escaped (via the inline escaper) — but the TOC label is not, so it's inconsistent.

**Evidence** (heading "Array[0] usage"):
```
- [Array[0] usage](#array0-usage)

# Array\[0\] usage
```
This particular case survives (balanced brackets are legal in link text), but a heading with *unbalanced* brackets (e.g. `Foo] bar`) would break the TOC entry while the heading body stays correct.

**Fix:** run the TOC label through `MarkdownText.escapeLinkText` (as `CardRenderer` already does).

---

## 3. Completeness gaps (lossy, not broken)

### Finding 8 — `mediaSingle`-level `link` mark is dropped · **Low/Med**

The schema allows a `link` mark on `mediaSingle` (`adf-schema.json:2290-2295`), making the whole image a link. `AdfAstParser.parseMediaSingle` (`:391-397`) never parses `marks`, and `MediaSingle` carries none, so the link is lost.

**Evidence:**
```
mediaSingle + link mark  →  ![pic](https://e.com/p.png)          (link lost)
media       + link mark  →  [![pic](https://e.com/p.png)](https://e.com/target)   (works)
```
A link mark on the inner `media` node *is* honored, so this is recoverable but the `mediaSingle`-level case is silently dropped. **Fix:** parse `mediaSingle` marks and, if a `link` is present, wrap the rendered media block.

### Finding 9 — Extension `text` fallback attribute ignored · **Low/Med**

`extension`/`bodiedExtension`/`inlineExtension` carry an optional `text` attr (`adf-schema.json:1352`, `423`, `1773` respectively) — the macro's textual fallback. `parseExtension` (`AdfAstParser.java:431-437`) doesn't capture it, and unknown macros render the generic `[Extension: type/key]` (`MacroRenderer.java:181-192`).

**Evidence** (`extensionKey:"someMacro"`, `text:"Rendered fallback text"`):
```
[Extension: com.atlassian.confluence.macro.core/someMacro]
```
Using `text` (when present) would convert otherwise-opaque macros into their human-readable fallback.

### Finding 10 — Block-level visual marks dropped (mostly acceptable)

`breakout` on `codeBlock`/`expand`/`layoutSection`/`syncBlock`, `fragment` on `table`, etc. are parsed inconsistently or dropped. These have no Markdown equivalent, so dropping is fine — noted only for completeness.

---

## 4. Design choices worth reconsidering (defensible, but challenge-worthy)

- **Header-less tables fall back to HTML** (`nodes/table-simple.md`, `table-header-row-not-first.md`). A data-only table could instead be a more readable GFM table with a synthesized empty header row (`|  |  |` + separator), which GitHub renders fine. HTML is safe but less "markdown-y". Design call — but the fixtures lock in the HTML choice.
- **A `taskList`/`decisionList` nested in a `listItem` is double-indented** (4 spaces vs the canonical 2). `ListRenderer.renderListItemBlock` (`ListRenderer.java:179-197`) threads the parent's exact child-indent only for `BulletList`/`OrderedList`; a `taskList` (schema-allowed in `listItem`, `adf-schema.json:1998-2001`) falls to the generic branch, which renders it with depth-based indent (`checklistPrefix`, `:222-225`) **and** applies the parent `childIndent` — counting the indent twice. This is cosmetic, not corruption: under a `- ` bullet the content column is 2, and 4 spaces is only 2 past it (well under the +4 that promotes to an indented code block), so both forms render to byte-identical HTML. Optional fix: give `TaskList`/`DecisionList` the same `childIndent`-threading as `BulletList`/`OrderedList`.
- **Inline `embedCard` → `[Unsupported inline: embedCard]`** (`inline/embed-card-as-inline-placeholder.md`). `parseInline` has no `embedCard`/`blockCard` case (`AdfAstParser.java:171-200`), so a (technically invalid) inline-positioned card with a real URL degrades to a generic placeholder instead of rendering its link. Recoverable input handled more harshly than necessary.
- **Empty paragraphs are elided** (`nodes/paragraph-empty.md`). Reasonable (CommonMark collapses blank lines anyway), but intentional spacer paragraphs vanish.
- **`version != 1` (or missing/stringy version) → empty body + diagnostics** (`AdfParsingService.java:72-78`). Strict and spec-correct, but a future minor version yields *nothing* rather than best-effort output.
- **`mediaGroup` images joined by a single newline** (`nodes/media-group.md`) → same paragraph / soft break, so they may render side-by-side. Minor stylistic choice.
- **Over-escaping** of `( ) _` in body text (`marks/escape-inline-metacharacters.md` → `\[br\]\(c\)`). Harmless to rendering (`\(`→`(`), but noisy if the Markdown is read as plain text.
- **`mention`/`status`/`placeholder` text bypasses inline escaping** (rendered verbatim). Negligible in practice — these rarely contain link/markdown metacharacters — but a `mention` text like `[x](y)` would form an accidental link.

---

## 5. Critique of the test suite

The suite is clean, auto-discovers fixtures, and even guards against duplicate payloads and the `reporte` regression avoiding raw HTML. But, judged against the spec rather than the implementation:

**Assumptions challenged:**
- `nodes/media-single-external.md` asserts `{width= height=}` is correct output — it isn't GFM (Finding 5).
- The HTML-`<table>` fixtures lock in the header-less-→-HTML policy (§4) as if it were the only option.
- `inline/embed-card-as-inline-placeholder.md` enshrines a generic placeholder where the URL is recoverable (§4).

**Coverage gaps (no fixture or assertion exists):**
- **TOC macro is never asserted end-to-end.** `anchor-macros.json` is used only for *outline metadata* (`AdfToMarkdownMetadataTests:79-86`); the actual `{{toc}}` body rendering in `renderTocMacro` (a substantial chunk of logic) has **zero** output coverage.
- No fixture exercises **HTML entities / `&`** in text (Finding 2).
- No fixture puts a **non-leaf block (codeBlock/heading/panel/blockquote/taskList/decisionList/rule) in a table cell** (Finding 3) — only lists and colspan/rowspan.
- No fixture has a **`hardBreak` inside a `heading`** (Finding 4).
- No fixture covers **adjacent same-mark runs** (Finding 1) — for any of strong/em/strike/code.
- No fixture nests a **taskList/decisionList inside a listItem** (§4).
- No fixture covers a **`mediaSingle` `link` mark** (Finding 8) or **unbalanced-paren URLs** (Finding 6).
- The **4-space indented-code** neutralization (`&#32;`) is fixture-untested (only ≤3-space promotion is covered); worth a direct fixture given it relies on entity decoding.

**Suggested new fixtures** (most are pure `.json`/`.md` pairs under `adf/spec/nodes|marks|inline`): a TOC-with-headings pair; `text` containing `&amp;`/`&#39;`; a header-row table with a `codeBlock` cell; a `heading` containing a `hardBreak`; adjacent same-mark text runs (strong **and** code); a `taskList` inside a `listItem`; a `mediaSingle` with a `link` mark; a link href with unbalanced parens; a paragraph with a leading 4-space run.

---

## 6. What's solid (so it isn't lost in a refactor)

- **Complete node/mark type coverage** with a clean unknown-node policy (PLACEHOLDER/SKIP/FAIL).
- **Careful inline escaping**: leading block-marker neutralization with ≤3-space tolerance and the 4-space indented-code guard; code-fence sizing by longest backtick run (`code-fence-collision`); code-span backtick padding; whitespace-aware mark wrapping (`~~Old~~ ~~value~~`, not `~~Old ~~~~…`).
- **Correct GFM tables**: header detection, column padding/min-width, ragged-row padding, `|`→`\|` and `\n`→`<br>` in cells, and a sound HTML fallback for number columns / spans / list cells.
- **Robust metadata extraction**: page/external/attachment dedup with first-seen ordering, Confluence page-id URL parsing, attachment resolution by normalized title (all well covered by tests).
- **Sensible panel→alert mapping** and **stable, GitHub-compatible heading slugs** (with `-1/-2` de-duplication) and explicit-anchor `<a id>` injection.

---

## 7. Prioritized recommendations

1. **Fix Finding 1** (coalesce adjacent identical-mark runs) — broadest corruption surface (all of strong/em/strike/code) and a localized change in `TextMarkRenderer` / the inline loop.
2. **Fix Finding 2** (escape `&`) — one-line change in `isInlinePunctuation`; prevents silent text corruption and resolves the converter's internal entity inconsistency. Safe: no existing `.md` fixture contains `&`, so nothing regresses.
3. **Fix Finding 3** (broaden `requiresHtmlTableFallback`) — use the full block list and the paragraph/image carve-out above. Safe against current fixtures (none place a non-leaf block in a cell).
4. **Fix Finding 4** (interior `hardBreak` in a heading → space, not `"  \n"`) — small, and also fixes the outline/anchor desync.
5. **Decide and document the target Markdown flavor**, then fix Finding 5 accordingly. The output today mixes GFM with Pandoc/commonmark-attributes image syntax; pick one story.
6. **Add the missing fixtures** in §5 — especially an end-to-end TOC assertion and a non-leaf-block-in-cell case — so the above fixes are regression-guarded.
7. Address Findings 6–9 and the §4 items opportunistically (low blast radius, low effort each).

*All findings were reproduced against the built library and, for every rendering claim, rendered to HTML through the bundled CommonMark; no source or fixtures were modified during this review.*
