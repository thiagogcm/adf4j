# adf4j — Adversarial Review (ADF → Markdown)

**Scope:** correctness and completeness of parsing/rendering, judged against the local ADF spec snapshot in `docs/spec/` (the draft-04 JSON Schema `adf-schema.json` and `structure.md`). **Date:** 2026-06-03. **Method:** independent read of the spec, the full implementation, and the test fixtures; the fixtures were treated as suspect rather than as ground truth. No source was changed — this is a review.

## Summary

adf4j is a carefully engineered converter. Against the spec, **node and mark coverage is effectively complete**: the parser recognises every node and mark the schema defines (and a few it doesn't, e.g. `multiBodiedExtension`/`extensionFrame`), and the renderer either renders each or drops it by an explicit, documented design choice. The hard parts of Markdown generation are handled with unusual care — literal-text escaping with leading-block-marker neutralisation, code-fence/code-span width selection, link-destination angle-wrapping, same-mark run coalescing, the GFM-vs-HTML table decision, and lenient/degrading parsing of malformed input are all correct and well-tested. The `CommonMarkOracleTests` re-parse the converter's own output and assert structural invariants, which is a genuinely strong test design.

The defects below are therefore **correctness nuances and completeness gaps, not missing node types**. They are ordered by impact. The single most important one (F1) is a systematic inconsistency that the current fixtures actively mask.

## Methodology

- **Spec basis:** catalogued every definition in `docs/spec/adf-schema.json` (3440 lines) and the node/mark inventory in `structure.md`, including each node's required attributes and allowed children/marks.
- **Implementation:** read the parser (`internal/parser/AdfAstParser.java`, `JsonFields.java`), the whole `internal/render/*` package, the engine (`internal/engine/*`), the public API (`Adf`, `AdfToMarkdown`, `options/*`), and the `ast/*` records.
- **Tests:** read `AdfSpecConversionTests` (exact-string match), `CommonMarkOracleTests` (structural oracle), `AdfMalformedInputTests`, and ~150 `adf/spec/**` JSON↔MD fixture pairs. Edge fixtures were traced by hand through the rendering algorithm to confirm or refute the expected output.

---

## Findings

### F1 — [High] Every inline node except `text` bypasses Markdown escaping

`AdfRenderer.renderText` (`AdfRenderer.java:411-422`) escapes literal text via `MarkdownText.escapeInlineText`, honouring an `atLineStart` flag that neutralises leading block markers. But `renderInline` (`AdfRenderer.java:256-272`) **ignores `atLineStart` for every non-`Text` case** and returns attribute-derived strings raw:

- `Placeholder` → `placeholder.text()` (`:266`), raw.
- `Mention` → `renderMention` (`:441-451`): `mention.text()` or `@<id>`, raw.
- `Emoji` → `renderEmoji` (`:433-439`): `text` or `shortName`, raw.
- `Status` → `renderStatus` (`:453-457`): `[<text>]`, inner text raw.
- `Date` → `MarkdownText.dateFromTimestamp` (`:263`) — on a non-numeric timestamp it returns the string **verbatim** (`MarkdownText.java:32-34`), raw.

Two distinct consequences:

1. **Inline corruption / link injection.** Any of these strings containing Markdown metacharacters (`` [ ] ( ) * _ ` ~ < & ``) is emitted literally and reinterpreted by the consumer. The fixture **`adf/spec/inline/placeholder.md` itself bakes this in**: placeholder text `[fill in name]` renders as `Owner: [fill in name]` with the brackets unescaped. It is benign *only* because that document has no `[fill in name]:` reference definition and nothing adjacent to combine with. A placeholder/mention/status whose text is `[label](http://evil)` becomes a **live link** (for `status` the bracket wrap makes it `[[label](http://evil)]`, which still yields a live `label`→`http://evil` link); a status text containing `]` (`a]b` → `[a]b]`) corrupts the lozenge token. Placeholders are bracketed prompts *by nature*, so they routinely emit raw `[...]` — whether that corrupts depends on adjacency, but the exposure is real and untested.
2. **Block promotion.** Because the `atLineStart` flag is dropped, an inline kind that emits its text *at column 0* — chiefly `placeholder` (free-form text), and in principle `mention` text or a non-numeric `date` fallback — that is a block's **first** inline and whose text begins with `#`, `-`, `+`, `>`, or `N.` is not neutralised and is promoted to a heading/list/quote by the consumer. Example: a paragraph whose sole inline is a placeholder `"# Title"` renders as an ATX heading. (`status` and `emoji` are *not* promotion vectors — their emitted form already begins with `[` or `:`/a glyph — but their *inner* text is still exposed to the corruption in (1).) `renderText` prevents exactly this for `Text` nodes; the other inline kinds slip past the same guard.

**Why it matters:** the renderer is rigorous about `Text` and silent about everything else — an asymmetry that is easy to miss precisely because the fixtures only ever feed these nodes benign payloads. `placeholder.md` is the only fixture that feeds one of these nodes a metacharacter at all, and it does so in a position where the unescaped output cannot change the parse; **no fixture places a `[`/`(`/`*` or a leading `#`/`-` where it would actually corrupt the result.**

**Fix:** route attribute-derived inline strings through `escapeInlineText(value, atLineStart)` — the flag is already passed into `renderInline`, so it only needs to be consumed in the non-`Text` arms. For `Status`, escape the inner text (and see F3 for the bracket token). Add fixtures that fuzz these node kinds with metacharacters and with a leading block marker.

### F2 — [Medium] All `media`/`mediaInline` render as Markdown images, even non-image attachments

`MediaRenderer.renderMediaBlock` (`MediaRenderer.java:74-86`) unconditionally emits `![alt](src)` for every media node. But ADF `media` covers arbitrary files: the schema `media_node` (`adf-schema.json:2024-2131`) allows `attrs.type` ∈ `file | link | external`, so a media node is routinely a PDF, video, Office document, or archive. For any non-image file, an image embed produces a **broken image** in every Markdown consumer (the alt text shows as a missing-image placeholder rather than a usable link).

The discriminating signal is already available: `MediaAttrs` parses `mediaType`, `__fileMimeType`, `__fileName`, and `name` (`AdfAstParser.java:497-509`), and `AdfContentMetadataExtractor.upsertAttachmentRef` (`:244-262`) even uses them to classify attachments — but `MediaRenderer` ignores all of it.

**Fix:** when the media is known to be non-image (mime/`mediaType` not `image/*`, or a non-image filename extension), emit a link `[name](src)` instead of `![alt](src)`; keep the image form when the type is unknown or image. The library javadoc (`AdfToMarkdown.java:17-19`) already acknowledges the lossy `media:` placeholder; classifying image vs. non-image is the adjacent, fixable half.

### F3 — [Medium] Bracketed label/placeholder tokens are emitted with unescaped delimiters

Several renderers emit literal `[...]` tokens whose brackets are never escaped, relying on **neighbouring** inlines being escaped to avoid forming reference/inline links: `Status` `[text]` (`AdfRenderer.java:453-457`), decision items `[decision]` / `[decision:state]` (`ListRenderer.java:235-244`), and the card/sync/extension/pdf/chart placeholders `[Card]`, `[Inline card]`, `[Embed card]`, `[Sync block]`, `[Extension: …]`, `[PDF]`, `[Chart]` (`CardRenderer.java:10-34`, `MacroRenderer.java:96-201`).

Today this is *mostly* safe because a following `Text` node escapes its parentheses — see fixture `nodes/status-then-paren.md` → `[Done]\(not a link\) follows`. But the safety is **incidental**, and F1 breaks it: an unescaped `(`-leading inline placed right after a `[Done]` — a `placeholder`, a `date` fallback, or an extension `text` fallback — yields `[Done](…)`, a real link whose destination is attacker- or author-controlled text.

**Fix:** escape the literal brackets in pure-label tokens (`\[Card\]`, `\[Done\]`, …). They render identically (`[Card]`) with zero visual cost but can no longer be misparsed. Fixing F1 closes the complementary hole on the neighbour side.

### F4 — [Low–Medium] Heading/TOC anchor slugs assume the consumer's slugger equals commonmark's

The `toc` macro (`MacroRenderer.renderTocMacro:136-166`) links to `#<anchor>`, where the anchor is produced by `AdfHeadingCollector.collect` (`:52-79`) via commonmark's `IdGenerator`. In-document headings receive **no injected id** — only explicit Confluence `anchor` macros do (`AdfRenderer.java:291-294`) — so the TOC link resolves only if the rendering platform (GitHub) auto-generates a heading slug that *matches* commonmark's. For ASCII headings they usually agree, but they can diverge on Unicode, punctuation runs (em-dash collapsing to `--`), emoji, and duplicate-name suffixing; and on any consumer that does not auto-assign heading ids at all, every TOC link is dead. The fixture `nodes/toc-macro.md` happens to align (`#details--draft`), which hides the fragility.

**Fix / note:** document the GitHub-slugger assumption explicitly, and/or inject `<a id="…">` on every TOC-referenced heading (the mechanism already exists for explicit anchors) so the links are self-contained and portable across renderers.

### F5 — [Low] Default `tableFallback = HTML` turns most headerless tables into raw HTML

`renderTable` (`TableRenderer.java:31-51`) only takes the native GFM path when the **first row consists entirely of `tableHeader` cells** (`firstRowIsHeader`); otherwise it applies `tableFallback`, whose default is `HTML` (`MarkdownOptions.java:22,28`). Any table whose first row is not entirely `tableHeader` (common when a table is authored without a styled header row) becomes a `<table>` blob under defaults rather than a GFM pipe table — surprising for a library whose stated target is GFM. This is a defensible fidelity-over-nativeness choice, but worth surfacing: `GFM_PROMOTE_FIRST_ROW` (already implemented and tested) may be the better default for "markdown-first" output.

### F6 — [Low] Silent, by-design drops that may warrant an opt-in

All defensible for a GFM target, but each discards information with no trace and could be made recoverable behind an option:

- Visual marks `textColor` / `backgroundColor` / `border` / `fontSize` are dropped (`TextMarkRenderer.isVisualOnlyHtmlMark:92-98`). An opt-in `<span style="…">` would preserve them for HTML-tolerant consumers.
- The paragraph/heading `indentation` mark is dropped — `renderParagraph` (`AdfRenderer.java:274-277`) ignores `paragraph.marks()` even though the mark is parsed (`AdfAstParser.java:118`). The intended nesting is lost.
- Panel-type fidelity collapses: `info` and `note` both map to `NOTE`, `success` to `TIP`, `custom`/unknown to `NOTE` (`gfmAlertType:358-369`). GFM has only five alert kinds and `IMPORTANT` is unused; a bolded label could retain the `success`/`note` distinction.

### F7 — [Low] Smaller robustness / cosmetic notes

- `mention` with no text falls back to `@<id>` (`AdfRenderer.java:446-449`); real ADF ids are opaque ARIs/UUIDs (`@557058:…`), so the fallback is unsightly. Consider an option or a neutral `@unknown`.
- Code-marked text containing a newline collapses to a single space inside the inline code span (inherent to CommonMark code spans) — acceptable, but undocumented.
- `escapeUrlDestination` (`MarkdownText.java:191-207`) leaves literal `<`/`>` in a destination when the URL itself contains them (a valid bare CommonMark destination), and does not percent-encode an embedded newline in that same branch. Both are pathological inputs the angle-wrapping logic cannot defend; worth a code comment.

---

## Fixtures challenged and found correct

To show the fixtures were genuinely interrogated rather than assumed, the following suspicious cases were traced through the algorithm and **confirmed correct** (not bugs):

- **`nodes/heading-with-explicit-anchor.md`** (`<a id="…"></a>\n## …`, single newline). This looked like an HTML-block-swallows-heading bug, but it is safe: `<a id="…"></a>` fails CommonMark HTML-block **start condition 7** (the open tag is *not* followed only by whitespace — `</a>` follows), so it parses as inline HTML inside a paragraph, and the ATX heading on the next line interrupts that paragraph. The anchor survives in a `<p>` and the heading renders as a heading.
- **`marks/code-span-edge-backtick`** — fence widening + space padding for backtick-bordering content is correct.
- **`marks/adjacent-code-runs`** — same-mark run coalescing into one `` `fooBar` `` is correct and intended.
- **`marks/code-with-link`** — `[`snippet()`](url)` matches the schema's `code_inline_node` allowing a `link` mark alongside `code`.
- **`marks/link-href-unbalanced-paren-open` / `-close`** — angle-wrapping `<…>` correctly preserves unbalanced parens in the destination.
- **`marks/link-blank-text-bracket-href`** — using the href as the visible label and escaping `]` in the label while leaving it raw in the destination is correct.
- **`nodes/code-fence-collision`** — a 4-backtick fence around a 3-backtick body is correct.
- **`nodes/ordered-list-start-zero`** — `order: 0 → "0."` is honoured by GFM.
- **`nodes/table-header-row-not-first`** — a non-first header row correctly forces the HTML fallback.
- **`nodes/table-gfm-cell-leading-marker`** — leading `#`/`-` inside a GFM pipe cell are correctly left unescaped (inline context, not a block marker), via the deliberate `TableCellKind.GFM` escaping suppression in `renderText` (`AdfRenderer.java:419`).
- **Always-escaping `&` and `~`** (`MarkdownText.java:128-133`) is correct, not over-zealous: an unescaped `&copy;` would be read as an HTML entity and a single `~` as a GFM strikethrough delimiter, so backslash-escaping both faithfully preserves the literal text.

## Suggested test additions

1. **Escaping fuzz for F1/F3:** placeholder/mention/status/emoji nodes whose text contains `[`, `]`, `(`, `)`, `*`, `_`, `` ` ``, `<`, `&` (inline-corruption coverage for all four); plus a `placeholder`/`mention`/non-numeric-`date` as a paragraph's first inline with text starting `# `, `- `, `> `, `1. ` (block-promotion coverage — assert via the CommonMark oracle that no heading/list/quote/link is produced). A `[status]` immediately followed by a `placeholder` whose text starts `(` exercises the F3 cross-node link injection.
2. **Non-image media for F2:** a `media`/`mediaInline` with `mediaType: "application/pdf"` (and a `.pdf` filename) — assert a link, not an image.
3. **Slug divergence for F4:** a `toc` plus headings with Unicode/punctuation/duplicate names — assert the emitted anchor matches what the in-document heading would slug to under the target platform.

## Closing assessment

The codebase is correct on the cases it tests and complete in node coverage. The exposure is concentrated in **untested inputs** — specifically, the asymmetry whereby `Text` is escaped but every other inline kind is not (F1/F3), and the assumption that all media are images (F2). Closing F1 is the highest-leverage change: it is a small, local edit (consume the `atLineStart` flag and reuse `escapeInlineText`) that removes a whole class of injection and block-promotion bugs the current suite cannot see.
