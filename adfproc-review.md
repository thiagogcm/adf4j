# adf4j — Adversarial Review: Parsing & Rendering Correctness

**Reviewed:** `adf4j-lib` parser + renderer pipeline against `docs/spec/adf-schema.json` (draft-04) and `docs/spec/structure.md`.
**Method:** spec-vs-code trace, adversarial input construction, and independent re-derivation of every golden fixture's expected output. Fixtures were treated as *claims to be falsified*, not as ground truth. Findings are tagged **[fixture-confirmed]** (a committed `.md` proves the behavior) or **[code-deduced]** (traced from source; no test exercises it).

**Validation:** every finding below was re-checked empirically in `jshell` against the compiled library by an independent reviewer. H1, H2, M1 (core), M2, M3, L1–L8, D1, D2 and T1–T6 were confirmed; **M4 was found over-stated and has been corrected and downgraded to Low** (deep nesting is safely caught as `INVALID_JSON`, not an escaping `StackOverflowError`).

---

## 1. Summary of findings

| ID    | Sev    | Area              | One-line                                                                                                                                                                                                                                   |
| ----- | ------ | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| H1    | High   | Inline escaping   | A `!`-terminated inline immediately before a link/titled-card renders as an accidental image `![…](…)`.                                                                                                                                    |
| H2    | High   | Tables            | Header cells that don't form an all-header *first* row are discarded; the GFM default promotes a **data** row to the header (header-column and header-row-not-first tables are mis-rendered).                                              |
| M1    | Med    | Link/card labels  | Card titles, href-fallback labels and TOC labels are only bracket-escaped, so `* _ ` ` ~ !` inside them parse as emphasis/code/image *inside* the link text.                                                                               |
| M2    | Med    | Anchors           | A Confluence `anchor` macro outside a heading is dropped (no `<a id>` emitted); an explicit anchor on an empty heading is lost. Intra-page links to those targets break.                                                                   |
| M3    | Med    | Mentions          | A mention with no inline `text` collapses to `@unknown` (identity lost); mentions are never collected into `ContentMetadata`.                                                                                                              |
| M4    | Low    | Robustness        | No *explicit* recursion-depth guard; deep nesting is already bounded safely by Jackson's JSON-nesting cap (~500 levels) and surfaces as a caught `INVALID_JSON`. Defense-in-depth only — revised down from Med after empirical validation. |
| L1    | Low    | Media             | A `type:"file"` media with no MIME/filename defaults to an **image** embed; non-image attachments are mis-embedded.                                                                                                                        |
| L2    | Low    | Marks             | `alignment` (center/end) is always dropped, even with `htmlVisualMarks` on.                                                                                                                                                                |
| L3    | Low    | URLs              | `escapeUrlDestination` leaves interior `<`/`>` and embedded newlines unencoded; pathological URLs can break the link.                                                                                                                      |
| L4    | Low    | Whitespace        | Empty/whitespace-only paragraphs collapse to nothing, losing intentional spacing.                                                                                                                                                          |
| L5    | Low    | Validation        | A string `version` (`"1"`) is rejected → the whole document is dropped to empty output.                                                                                                                                                    |
| L6    | Low    | Panels            | Label asymmetry: `note` emits a `**Note**` line, `info` does not, though both map to `[!NOTE]`.                                                                                                                                            |
| L7    | Low    | Perf              | `coalesceAdjacentText` uses repeated string concatenation → O(n²) for many tiny same-mark runs.                                                                                                                                            |
| L8    | Low    | Dates             | Epoch is assumed to be milliseconds; a (non-spec) seconds timestamp silently renders a 1970 date.                                                                                                                                          |
| D1    | Design | AST               | `MediaAttrs` is a 12-arg positional record built positionally — a silent-reordering hazard, inconsistent with the generic `Attributes` used by cards.                                                                                      |
| D2    | Design | AST               | Inline `embedCard`/`blockCard` are folded into `InlineCard`, erasing type identity.                                                                                                                                                        |
| T1–T6 | Test   | Fixtures/coverage | Two fixtures enshrine the H2 bug as "expected"; H1/M1/M2/M4 have no coverage. See §6.                                                                                                                                                      |

**Bottom line:** node-type *coverage* is essentially complete (every schema node and mark has a handler; unknowns degrade safely). The real risks are **fidelity** bugs — H1 and H2 produce visibly wrong Markdown for plausible, in-spec inputs and should be fixed first.

---

## 2. What the implementation gets right (so the criticism is calibrated)

These are genuinely well done and should not be regressed by any fix:

- **Sealed-interface AST + exhaustive `switch`** (`AdfBlock`/`AdfInline`/`AdfMark`): the compiler guarantees every known type is handled, and `UnknownBlock/Inline/Mark` keep raw JSON for forward-compatibility (`AdfAstParser.java:158,205,247`).
- **Inline escaping is mostly excellent**: one-pass punctuation escaping with lazy allocation, intra-word `_` left literal (CommonMark-correct), per-line leading-block neutralization after embedded newlines, and a clean separation of "at line start" vs mid-line (`MarkdownText.escapeInlineText`). The `escape-inline-metacharacters`, `escape-underscore-intraword`, and `escape-block-promotion` fixtures are all correct.
- **Code-fence and code-span sizing**: the fence/backtick run is computed to exceed the longest embedded run (`renderCodeBlock`, `wrapCodeSpan`, `longestBacktickRun`) — `code-fence-collision` is correct.
- **Deterministic dates**: epoch-millis → `Instant … ZoneOffset.UTC … toLocalDate()` is JVM/timezone-independent (`MarkdownText.dateFromTimestamp`); `date.md` (`1745366400000` → `2025-04-23`) checks out.
- **Anchor injection for slugger divergence**: TOC-referenced and explicit-anchor headings get an injected `<a id>` whose id equals the TOC link target, so links resolve even when the consumer's slugger differs (`toc-slug-divergence` is a strong fixture).
- **The CommonMark "oracle" tests** (`CommonMarkOracleTests`) — re-parsing the converter's own output and asserting structural invariants — is an excellent, high-value testing idea and catches a class of bugs golden-file diffs miss.
- **Lenient/degrading parser**: JSON `null`s, mis-nested children, and stray inlines are dropped, never thrown (`AdfMalformedInputTests` all pass by construction).
- **Clean layering**: product-neutral AST + isolated Confluence layer reading from a generic `Attributes` map; `RenderContext` (immutable config) vs `RendererState` (moving cursor) is a tidy split.

---

## 3. High-severity findings

### H1 — `!` before a link/titled-card produces an accidental image `![…](…)`  **[code-deduced]**

**Where:** `MarkdownText.isInlinePunctuation` (`MarkdownText.java:133-138`) escapes `\ ` ` * [ ] ( ) ~ < &` — **but not `!`**. Link rendering emits a literal `[` (`TextMarkRenderer.java:75`), as do all titled cards (`CardRenderer.java:50,56`). `renderInlineNodes` concatenates adjacent inline renderings with **no separator** (`AdfRenderer.java:198-210`).

**The bug:** when one inline's rendered text ends in `!` and the next inline emits a leading `[`, the boundary forms `![…](…)` — a CommonMark image. The `!` is never escaped, so this fires silently.

**Concrete repro (in-spec, very plausible):**

```json
{"type":"doc","version":1,"content":[{"type":"paragraph","content":[
  {"type":"text","text":"Heads up!"},
  {"type":"text","text":"the runbook","marks":[{"type":"link","attrs":{"href":"https://ex.com/r"}}]}
]}]}
```

Current output: `Heads up![the runbook](https://ex.com/r)` → renders as the word "Heads up" followed by a **broken image** with alt text "the runbook". Expected: the literal text "Heads up!" followed by a hyperlink.

Also fires via: text/mention/placeholder ending in `!` → inline card with a title (`[name](url)`), embed/block card with a title, or a media **link**. (URL-only cards emit `<url>` and are safe; media *images* already begin with `!` and are unaffected.)

**Why it matters:** "!" is one of the most common sentence-final characters; placing a link right after an exclamation is ordinary prose. This is the one real hole in an otherwise-rigorous escaping layer, and **no fixture exercises it** (grep for a `!`-terminated text node before a link finds none).

**Fix options (pick one):**

1. Add `!` to `isInlinePunctuation`. Simplest and fully safe — `\!` renders as a literal `!` in every context (including GFM cells). Cost: every literal `!` becomes `\!` in the Markdown *source* (visually identical output, slightly noisier source). This matches the library's already-aggressive escaping of `~`, `&`, `<`.
2. Targeted boundary guard: in `renderInlineNodes`/`renderHeadingInlines`, if the accumulated output ends with `!` and the next rendered fragment begins with `[`, insert a `\` (or escape the trailing `!`). Avoids over-escaping sentence-final `!` but adds boundary logic in two places.

Recommendation: option 1 for correctness simplicity unless source-noise is a stated concern, in which case option 2.

---

### H2 — Header cells outside an all-header first row are scrambled; a data row is promoted to the GFM header  **[fixture-confirmed]**

**Where:** `TableRenderer.renderTable` (`TableRenderer.java:39-51`) routes to HTML only when `numberColumn || requiresHtmlTableFallback(rows)`. `requiresHtmlTableFallback` (`160-174`) checks **only** colspan/rowspan and non-GFM cell content — it never inspects where `tableHeader` cells sit. `firstRowIsHeader` (`144-153`) returns true only when the *first* row is entirely headers. Any other header placement falls through to the `tableFallback` policy, whose default (`GFM_PROMOTE_FIRST_ROW`) blindly relabels the first emitted row as the header.

**Two mis-renderings, both committed as "expected":**

*Header column* (`nodes/table-header-column-gfm`): input rows are `[th "R1H", td "r1c2"]`, `[th "R2H", td "r2c2"]` — i.e. the **left column** is row-headers. Committed output:

```
| R1H | r1c2 |
| --- | ---- |
| R2H | r2c2 |
```

The reader now sees `R1H` / `r1c2` as **column titles** and `R2H` / `r2c2` as a body row — the header semantics are inverted. The HTML fallback would faithfully emit `<th>R1H</th><td>r1c2</td>` per row.

*Header row not first* (`nodes/table-header-row-not-first`): input is a data row `[td d1, td d2]` then a header row `[th H1, th H2]`. Committed output:

```
| d1  | d2  |
| --- | --- |
| H1  | H2  |
```

The **actual headers H1/H2 are demoted to a body row**, and the data d1/d2 is promoted to the header. This is unambiguously wrong.

**Why it matters:** key-value/property tables (header in the left column) are extremely common in Confluence, and a header row that isn't physically first is legal ADF. GFM cannot express row-headers or a non-first header row — but the library *already has a faithful path* (the HTML `<table>` fallback that preserves `<th>` positions). The default instead picks the most misleading option: presenting data as a header.

**Fix:** in `requiresHtmlTableFallback` (or a sibling check), also return true when the table contains any `tableHeader` cell **and** `firstRowIsHeader` is false — route such tables to the HTML fallback so `<th>` placement is preserved. (Alternatively, for the GFM policies, fall back to `GFM_EMPTY_HEADER` rather than promoting a real row, so at least no data is mislabeled as a header.) Then correct the two fixtures (see T1).

---

## 4. Medium-severity findings

### M1 — Link/card/TOC labels are under-escaped (`escapeLinkText` only handles brackets)  **[code-deduced]**

`MarkdownText.escapeLinkText` (`MarkdownText.java:44-46`) escapes only `[` and `]`. It is used for the link href-fallback label (`TextMarkRenderer.java:71`), all card titles (`CardRenderer.java:50,60`), the media link label (`MediaRenderer.java:80`), and TOC entry labels (`MacroRenderer.java:152`). Any `* _ ` ` ~` in those attribute-derived strings is emitted **inside** `[ … ]` and re-parsed: e.g. an inline card whose `data.name` is `Use *bold* now` renders `[Use *bold* now](url)` → emphasis inside the link text; a name containing a backtick → a code span. (Note: a `!` *inside* the label, e.g. `[Done!](url)`, is harmless — it does not form an image, since the H1 image vector needs `!` immediately *before* the `[`.) Normal text links are unaffected (their label is already fully `escapeInlineText`-escaped); only attribute-sourced labels leak.

**Fix:** route these labels through full inline escaping (`escapeInlineText(label, false)`) instead of `escapeLinkText`, or extend `escapeLinkText` to also escape `* ` ` _ ~ !`. (`(`/`)` inside `[ … ]` are harmless, so either approach is safe.)

### M2 — `anchor` macro outside a heading is dropped; empty-heading anchors lost  **[code-deduced]**

`MacroRenderer.renderExtensionCore` maps `case "anchor" -> ""` (`MacroRenderer.java:49`), and `AdfHeadingCollector.extractAnchorId` only looks inside **heading** content. So a Confluence `anchor` macro placed in a paragraph (a normal way to define a link target) renders to nothing and emits no `<a id>`, breaking any intra-page link that targets it. Separately, `renderHeading` returns `""` for blank-text headings *before* the anchor-injection branch (`AdfRenderer.java:286-289`), so an explicit anchor on an otherwise-empty heading is also lost. That anchor is additionally dropped from `ContentMetadata.outline()` (the blank heading is skipped by the collector), so outline/TOC resolution loses the target too — not just the inline `<a id>`.

**Fix:** emit `<a id="…"></a>` for a standalone `anchor` macro (it already has the id via `ConfluenceSupport.anchorId`); inject the anchor for an empty heading before eliding the (empty) text.

### M3 — Mention without `text` → `@unknown`; mentions absent from metadata  **[fixture-confirmed]**

`renderMention` falls back to the literal `@unknown` when `text` is blank (`AdfRenderer.java:471-475`; `mention-without-text.md` confirms `Owner: @unknown`). Stored ADF very often omits the inline `text` (it is resolved from `id` at display time), so distinct users all collapse to one indistinguishable token, and the `id` (`user-9`) is discarded. `AdfContentMetadataExtractor.collectInline` also never records mentions, so `ContentMetadata` has no notion of who was mentioned.

**Fix:** make the fallback configurable (e.g. `@` + a short form of `id`/`accountId`, or keep `@unknown` but log), and consider a `MentionReference` in `ContentMetadata` so callers can resolve identities. At minimum, document the lossiness. (Symmetric, rarer gap: an `emoji` carrying only an `id` — no `text`/`shortName` — renders to empty.)

---

## 5. Low-severity findings & design notes

- **L1 — Media defaults to image embed** (`AttachmentReferences.isImage`, `AttachmentReferences.java:46-52`): unknown classification returns `true`, and `MediaRenderer.isImage` only consults MIME/filename, never `attrs.type`. A spec-minimal `{"type":"file","id","collection"}` with no `__fileMimeType`/`__fileName` renders as `![…](media:…)` (`media-single-file.md` confirms). For a real non-image attachment that lacks metadata, this embeds a broken image. The code comments this as an intentional tradeoff; at least surface it in docs, or treat `type:"file"` with no image signal as a link.
- **L2 — `alignment` mark always dropped** (`TextMarkRenderer.applyInlineMark:140-155`, `renderParagraph:277-282` applies only `indentationPrefix`): paragraph/heading center/end alignment is silently lost even under `htmlVisualMarks`. Markdown can't express it inline; if fidelity matters, wrap the block in `<div align="…">` under the HTML-visual opt-in, else document it as always-dropped.
- **L3 — URL edge cases** (`escapeUrlDestination`, `MarkdownText.java:196-214`): when a URL already contains `<`/`>` the angle-wrap path is skipped and only spaces/parens are percent-encoded, leaving the `<`/`>` and any embedded newline unencoded, which *can* break the `( … )` destination for pathological URLs (in the common cases tested, CommonMark still parsed a valid link). The code flags this as a known limitation; consider percent-encoding `<`/`>`/newline too.
- **L4 — Empty paragraphs collapse** (`paragraph-empty.md`): consecutive empty paragraphs all vanish, losing intentional vertical whitespace. Usually fine; note it.
- **L5 — String `version` rejected** (`AdfParsingService.validateRoot:74-80`): `version:"1"` yields `INVALID_VERSION` (fatal) → empty output, while `version:2` (number) renders best-effort. A producer that stringifies the version loses the whole document. Consider accepting a numeric-string `version` leniently (parse, warn) as is done for unsupported numeric versions.
- **L6 — Panel label asymmetry** (`gfmAlert`, `AdfRenderer.java:384-396`): `note`→`[!NOTE]` + `**Note**`, but `info`→`[!NOTE]` with no label; both render as NOTE. The extra label on `note` only (visible in `reporte.md`) is inconsistent. Either label both or neither.
- **L7 — `coalesceAdjacentText` quadratic concat** (`AdfRenderer.java:217-242`): merging N adjacent same-mark `Text` nodes does `pending.text() + text.text()` each step → O(total²). Pathological but cheap to fix with a `StringBuilder` accumulator.
- **L8 — Epoch unit assumption** (`dateFromTimestamp`): a non-spec seconds timestamp (10 digits) silently renders a 1970 date. Optional: if the parsed value is implausibly small, treat as seconds.
- **M4 (revised to Low after empirical validation) — no *explicit* recursion-depth guard** (`AdfAstParser.parseBlock` and `AdfRenderer.renderBlock` recurse unbounded): in practice this is bounded safely and does **not** crash. Jackson's `StreamReadConstraints` nesting cap (~500 JSON levels ⇒ ≈250 nested ADF blocks, since each block is `{…}`+`[…]` = 2 levels) rejects deeper input, and the resulting `StreamConstraintsException` **is a `JacksonException`**, so it is caught by `AdfParsingService` and surfaces as a clean `INVALID_JSON` diagnostic with an empty body — no `Error` escapes, and the "never throws" contract holds. Parse and render are also sequential phases (parse completes and returns the AST before render walks it), so they are not co-resident on one stack. *Verified in jshell:* 240 nested blockquotes render fine; 260 → `INVALID_JSON` with no throw. The recommendation is therefore **defense-in-depth only** — optionally add an explicit traversal-depth guard so the bound is self-owned rather than inherited from Jackson's default; there is no reachable crash today.
- **D1 — `MediaAttrs` 12-arg positional record** (`MediaAttrs.java`, constructed positionally at `AdfAstParser.java:497-509`): twelve same-typed `String` fields built by position is a silent-reorder hazard and inconsistent with the generic `Attributes` map used for cards. Order is currently correct, but consider a builder or grouping.
- **D2 — Inline embed/block cards folded into `InlineCard`** (`AdfAstParser.java:182-183`): pragmatic and lenient, but the AST loses the distinction; fine for Markdown, worth a note for any future non-Markdown renderer.

---

## 6. Test-suite & fixture critique (the brief: don't trust the fixtures)

The suite is unusually good (golden files + an independent CommonMark structural oracle + malformed-input degradation + a "no duplicate payloads" guard). The gaps:

- **T1 — Two fixtures enshrine the H2 bug as correct.** `nodes/table-header-column-gfm.md` and `nodes/table-header-row-not-first.md` assert the scrambled/promoted output. They should instead assert the HTML fallback (preserving `<th>`), and the code should be fixed to produce it. As written, they actively protect a fidelity bug from regression detection.
- **T2 — No coverage for H1** (`!` before a link/card → image). The escaping suite is otherwise thorough; this is the conspicuous omission. Add a fixture: text `Heads up!` + link, asserting an `<a>` (not `<img>`) via the oracle.
- **T3 — No coverage for M1** (markdown metacharacters inside card/TOC labels). Add an inline-card whose `data.name` contains `*`/`` ` ``/`!` and assert no stray `<em>`/`<code>`/`<img>`.
- **T4 — No regression test pinning the deep-nesting bound** (M4). Add a deeply-nested input asserting `toMarkdown` returns cleanly with an `INVALID_JSON` diagnostic and an empty body past Jackson's nesting cap (the current safe behavior), so the bound is pinned against future config drift — *not* a test that guards against an `Error`, since none occurs.
- **T5 — No fixture for a standalone (non-heading) `anchor` macro** (M2).
- **T6 — Oracle coverage is inline/paragraph-centric.** The structural oracle (the strongest tool here) is applied mostly to inline cases; extend it to tables (header placement), panels/alerts, expand/`<details>`, and nested lists so structural regressions in block constructs are caught too. Also consider asserting that *intended* drops (visual marks) are deliberate, e.g. a negative assertion that the text survives even though the color is gone (already done for `textColor`; generalize).

Fixtures independently re-derived and confirmed **correct**: `date`, `date-non-numeric`, `subsup-html`, `underline-html`, `strong-em-strike-stacked`, `adjacent-strong-runs`, `code-with-link`, `link-href-with-space`, `link-href-unbalanced-paren-open`, `text-with-html-entities`, `escape-inline-metacharacters`, `escape-block-promotion`, `escape-text-node-embedded-newline`, `table-simple`, `table-with-header-row`, `table-number-column`, `table-with-colspan-html-fallback`, `table-cell-hard-break`, `table-gfm-leading-marker-unescaped` (GFM cells are inline context — leaving leading markers unescaped is correct), `code-fence-collision`, `code-block-with-language`, `ordered-list-start-zero`, `list-item-multi-paragraph`, `blockquote-multi-paragraph`, `heading-with-hard-break`, `heading-with-inline-image`, `image-alt-with-bracket`, `toc-slug-divergence`, `toc-card-in-heading`, `panel-info`, `panel-success`, `inline-card-url`, `inline-card-data-only`, `embed-card-as-inline-link`, `media-group`, `media-single-file`, `decision-list`, `sync-block`, `multi-bodied-extension`, `status` / `status-then-paren`, `hard-break`, `layout-section`, `paragraph-empty`.

---

## 7. Spec-coverage matrix (completeness)

Node-type coverage is **complete** — every schema node and mark, plus the structure-doc-only `multiBodiedExtension`/`extensionFrame`, has a handler; unrecognized types degrade via `UnknownNodePolicy`. Coverage gaps are *fidelity*, not *missing handlers*:

| Marks                                             | Status                                                                          |
| ------------------------------------------------- | ------------------------------------------------------------------------------- |
| strong, em, strike, code, link, underline, subsup | rendered ✓                                                                      |
| indentation                                       | rendered as U+00A0 run ✓ (verified: `INDENT_UNIT` is 4×U+00A0, comment-correct) |
| textColor, backgroundColor, border, fontSize      | visual — dropped unless `htmlVisualMarks` ✓ (by design)                         |
| annotation, breakout, fragment, dataConsumer      | structural/invisible — text passthrough ✓ (correct)                             |
| **alignment**                                     | **always dropped (L2)** — even under `htmlVisualMarks`                          |

| Inline                                                                                                   | Status                                               |
| -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| text, code-inline, date, emoji, hardBreak, inlineCard, placeholder, status, inlineExtension, mediaInline | rendered ✓                                           |
| **mention**                                                                                              | rendered, but `@unknown` fallback + no metadata (M3) |

| Block                                                                                                                                                                                                                                                                                                                  | Status                                                   |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| doc, paragraph(+marks), heading(+marks), blockquote, codeBlock, panel, rule, bullet/ordered/list-item, task/blockTask, decision, table/row/cell/header, mediaSingle/group/media/caption, expand/nestedExpand, layoutSection/column, extension/bodied/multiBodied/extensionFrame, sync/bodiedSync, blockCard, embedCard | rendered ✓                                               |
| **table (header placement)**                                                                                                                                                                                                                                                                                           | mis-routed for header-column / non-first-header-row (H2) |

---

## 8. Prioritized recommendations

1. **H1** — escape `!` (or guard the inline boundary). Highest impact-to-effort; closes the last escaping hole.
2. **H2** — route tables with non-canonical header placement to the HTML fallback; fix `table-header-column-gfm` / `table-header-row-not-first` fixtures (T1).
3. **M1** — use full inline escaping for attribute-derived link/card/TOC labels.
4. **M2 / M3** — emit a standalone-anchor `<a id>` (also inject it for empty headings and record it in the outline); make the mention fallback configurable + (optionally) collect mention refs. **M4 (Low)** — optional defense-in-depth only: add an explicit traversal-depth guard; there is no reachable crash today (Jackson's nesting cap already bounds it and the overflow is caught).
5. **Tests** — add T2–T5 fixtures and extend the structural oracle to block constructs (T6).
6. **Low/design** — address L1–L8, D1–D2 opportunistically and document the intentional lossy behaviors (visual marks, alignment, media-as-image default, URL-scheme non-sanitization) in `docs/markdown-conversion.md`.

---

## Verification (for the report itself / for any later fixes)

- **Reproduce H1:** `AdfToMarkdown.create().toMarkdown(<repro in §H1>)` → observe `Heads up![the runbook](…)`; confirm via `CommonMarkOracleTests`-style parse that it yields an `<img>`, not an `<a>`.
- **Reproduce H2:** run the two named fixtures through `AdfSpecConversionTests` and compare against an HTML-fallback expectation; or `toMarkdown` the inputs and confirm a data row sits above the separator.
- **Regression baseline:** `./mvnw -q test` (Java 25 required per the enforcer) to confirm the current suite is green before changing anything.
- Each finding above cites `file:line` so a fix can be located and re-verified directly.
