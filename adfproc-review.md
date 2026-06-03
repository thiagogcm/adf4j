# adf4j — ADF → Markdown correctness & completeness review

**Reviewer:** independent systematic pass over `adf4j-lib` (parser, AST, renderers, engine, metadata) cross-checked against `docs/spec/adf-schema.json` (draft-04, fetched 2026-05-25), `docs/spec/structure.md`, and `docs/markdown-conversion.md`.
**Method:** read every production source file and every test/fixture; cross-referenced each node/mark against the schema; then compiled the library (`./mvnw -o -pl adf4j-lib compile`, JDK 25) and ran throwaway harnesses against the real `dev.nthings.adf4j.Adf` to empirically confirm or reject each hypothesis (the bundled `org.commonmark` parser was used as a structural oracle, exactly as `CommonMarkOracleTests` does). Every "Evidence" block below is real harness output, not a guess. I also ran **falsification passes** — actively trying to break each finding (e.g. checking the GFM-table path does *not* exhibit the HTML-path bug, and confirming two cases I initially suspected — task-item hard breaks and double-`<br>` — are actually correct, so they are *not* reported as bugs). Tests and fixtures were treated as suspect, per the brief.

**Confidence:** every numbered finding below was reproduced from real output. The one place I cannot test the *host's* behavior offline is Finding 7's interaction with GitHub specifically — but I close that gap by proving the divergence against the project's own bundled commonmark, which is a stronger demonstration than a GitHub comparison would be.

**Overall:** the implementation is genuinely high quality. Inline escaping, link/URL destination handling, code-fence collision, GFM-table-vs-HTML-fallback policy, the smart-link/JSON-LD card logic, and the malformed-input degradation are all careful and, in most cases, correct. The findings below are the exceptions — a small number of real correctness bugs, one of which is locked in by a test, plus completeness gaps and lower-priority polish.

---

## Findings at a glance

| # | Severity | Area | One-line summary |
|---|----------|------|------------------|
| 1 | **High** | Rendering / marks | `strong` and `em` are silently stripped from **heading** text (only emphasis; `code`/`strike`/`link`/`underline`/`subsup` survive). Lossy, inconsistent, and locked in by a test. |
| 2 | **Medium** | Tables (HTML fallback) | Literal cell text that begins with a block marker (`#`, `>`, `-`, `+`, `1.`) is re-parsed and promoted to a block element (`<h1>`, `<blockquote>`, `<ul>`…) inside the cell. Hits the **default** path for every header-less table. |
| 3 | **Medium** | Parser + renderer / task lists | A `taskList` nested inside a `taskList` (schema-valid) is dropped entirely — both the parser and the renderer discard it. Silent data loss. |
| 4 | **Low–Med** | Cards | A URL-only card whose `url` has no scheme (relative Confluence link) is emitted as `<url>`, which is not a valid autolink and renders as escaped literal text, not a link. |
| 5 | **Low–Med** | Completeness | `multiBodiedExtension` and `extensionFrame` (top-level/child nodes per `structure.md`) are unhandled → placeholder, bodies lost. |
| 6 | **Low** | Lists | A `taskList`/`decisionList` nested directly under a `listItem` is double-indented (4 spaces) vs. the correct marker-aligned 2 spaces that `bulletList`/`orderedList` get. |
| 7 | **Low–Med** | Headings / TOC | **Proven dead link:** a `toc` macro links to a heading slug built from an inline card's **URL**, but the heading's actual anchor is built from the card's **title** — mismatch reproduced against the project's own bundled parser. |
| 8 | **Low** | Escaping | `_` is unconditionally backslash-escaped even intra-word (`snake_case` → `snake\_case`), where CommonMark never treats it as emphasis. Output noise, not a correctness bug. |
| 9 | **Nit** | API / robustness | A `version` other than `1` makes `convert` return an **empty body** (not best-effort). Forward-incompatible if/when ADF v2 ships. |

---

## High

### 1. `strong`/`em` are stripped from heading text during rendering

**Locations:**
- `internal/render/AdfHeadingCollector.java:168` `stripEmphasisMarks(...)` removes `Strong`/`Em` marks from `Text`/`MediaInline`.
- `AdfHeadingCollector.java:82` `normalizedHeadingNodes(...)` applies `stripEmphasisMarks` to every node.
- `internal/render/AdfRenderer.java:294` `renderHeadingText(...)` calls `normalizedHeadingNodes(...)` **and then renders the result** (`renderHeadingInlines`, `AdfRenderer.java:306`). So the stripping is on the render path, not just the slug path.
- Locked in by `internal/render/AdfHeadingCollectorTests.java:28` — argument set literally named *"emphasis marks are removed before heading markdown is rendered"*, asserting `[strong, code, em]` → keeps only `code`.

**Evidence (harness):**
```
ADF heading content: "Plain and " + "bold"(strong) + " and " + "italic"(em)
→  ## Plain and bold and italic            # bold + italic GONE

ADF heading content: "see "(strike) + "docs"(link) + " run"(code)
→  ## ~~see~~ [docs](https://x.com)` run`   # strike, link, code ALL survive
```

**Why this is wrong:**
1. **Lossy.** `## **Important** changes` is valid GFM and renders bold-within-heading on GitHub and via the project's own bundled `commonmark`. The emphasis carries authorial intent (a partially-bold heading, an italicised term) that is thrown away.
2. **Inconsistent.** There is no principled reason emphasis is special: `code`, `strike`, `underline`, `subsup`, and `link` are all preserved inside headings (confirmed above). Only `strong`/`em` are removed. The asymmetry strongly suggests this was not a deliberate fidelity decision but an over-reach.
3. **The "slug" justification doesn't hold.** The plain-text used for the anchor (`extractHeadingPlainText`, `AdfHeadingCollector.java:105`) reads `text.text()` directly and **ignores marks entirely** — so stripping emphasis has *zero* effect on slug generation. The strip exists *only* to alter the visible heading. (Verified: `heading-only-bold` still slugs to `all-bold` and renders `## All Bold`.)

**Note for the brief:** this is exactly the kind of behavior the test suite "locks in." `AdfHeadingCollectorTests` asserts it as correct; I'm asserting it is not. No `.json/.md` conversion fixture exercises an emphasized heading at all (the only fixtures with `strong` on heading text — `especificacoes-reporte-children.json`, `lista-participantes-viewpdf.json` — are JSON-only support fixtures with **no** `.md` expectation), so the rendered behavior is effectively untested at the snapshot level while being pinned by the collector unit test.

**Recommendation:** keep emphasis on the render path. `normalizedHeadingNodes` currently does three things — trim leading/trailing `hardBreak`, drop the `anchor` inline-extension, strip `strong`/`em`. Split out the third: keep (a) and (b) for rendering, drop (c). The slug path is unaffected (it never looked at marks). Then update the collector test and add a `nodes/heading-with-emphasis.{json,md}` conversion fixture asserting `## Plain and **bold** and *italic*`.

---

## Medium

### 2. HTML-fallback table cells promote literal leading-marker text to block HTML

**Locations:**
- `internal/render/TableRenderer.java:267` `renderHtmlTableCellLeafBlock` renders the cell block to Markdown, then…
- `internal/render/MarkdownRenderingSupport.java:18` `renderHtmlDocument` runs that Markdown back through `Parser.parse(...)` **as a full document**.
- Root cause: `internal/render/AdfRenderer.java:414` suppresses leading-block-marker neutralization whenever `context.inTable()` (`atLineStart && !context.inTable()`). That suppression is correct for a *GFM* cell (inline-only context) but wrong here, because the HTML path re-parses the cell text as block-level Markdown.

**Evidence (harness, default options, header-less 2×2 table → HTML fallback):**
```
cell "# hash"   →  <td><h1 id="hash">hash</h1></td>
cell "> quote"  →  <td><blockquote>\n<p>quote</p>\n</blockquote></td>
cell "- item"   →  <td><ul>\n<li>item</li>\n</ul></td>
cell "plain"    →  <td>plain</td>          # unaffected
```

**Falsification check (confirms the bug is HTML-path-specific):** the *same* cell text in a **GFM** table (header-row present, so the GFM path is taken) stays literal — `| # hash | - item |` → `<td># hash</td><td>- item</td>`. So the GFM path is already correct and must **not** be changed; only the HTML-fragment path is broken. This also rules out "neutralize everywhere" as the fix.

**Why this matters:** the HTML fallback is the **default** rendering for *any* table whose first row is not all-`tableHeader` (`tableFallback` defaults to `HTML`), plus every table with a number column / `colspan` / `rowspan` / non-GFM cell. So a perfectly ordinary header-less table containing a cell that merely *starts with* `#`, `>`, `-`, `+`, or `1.` silently turns that literal text into a heading/quote/list inside the cell. The text content is corrupted, not just restyled.

No fixture covers this — `table-with-code-block-cell-html-fallback` and friends use cell text that doesn't begin with a marker.

**Recommendation:** the cell text handed to `renderHtmlFragment` must be neutralized at block starts. Cleanest options:
- Render the cell block with leading-block-marker neutralization **enabled** when the destination is the HTML-fragment path (i.e. don't let `inTable` suppress it on that path), or
- Have `MarkdownRenderingSupport` treat cell content as an inline fragment (parse with an inline-only configuration / a leading sentinel) rather than a full document, so `# x` can never become `<h1>`.

Add fixtures: a header-less table and a colspan table each with a cell whose paragraph begins with `#`, `-`, `1.`, `>`.

---

### 3. A `taskList` nested inside a `taskList` is dropped entirely

**Locations:**
- Parser: `internal/parser/AdfAstParser.java:312` `parseTaskListItems` keeps only `taskItem`/`blockTaskItem` and `continue`s past anything else — so a nested `taskList` child is discarded at parse time.
- Renderer: `internal/render/ListRenderer.java:21` `renderTaskList` switches only on `TaskItem`/`BlockTaskItem`; even if a nested list survived, it would not be rendered.

**Schema:** `taskList_node.content` is `anyOf [taskItem, taskList, blockTaskItem]` (`adf-schema.json` ~line 3334) — a nested `taskList` is explicitly valid ADF (indented sub-checklists are a real Confluence/Jira construct).

**Evidence (harness):**
```
taskList[ taskItem(DONE "Parent"), taskList[ taskItem(TODO "Child") ] ]
→  - [x] Parent          # "Child" and the whole sub-list vanished
```

**Recommendation:** in `parseTaskListItems`, also recurse into `taskList` children (parse them as `TaskList`). In `renderTaskList`/`ListRenderer`, handle a nested `TaskList` by re-entering with an incremented `listDepth` so the sub-checklist is indented like a nested bullet list. Add a `nodes/task-list-nested.{json,md}` fixture.

---

## Low–Medium

### 4. URL-only cards with a scheme-less URL emit an invalid autolink

**Location:** `internal/render/CardRenderer.java:54` — for a card that has a URL but no title, the code emits `<url>` when `escapeUrlDestination(url).equals(url)` (i.e. the URL is "clean").

**Evidence (harness, rendered to HTML via the bundled commonmark oracle):**
```
blockCard url="/wiki/x/123"  →  MD: </wiki/x/123>
                              →  HTML: <p>&lt;/wiki/x/123&gt;</p>     # literal text, NOT a link
```
A CommonMark autolink (`<...>`) requires an absolute URI with a scheme; a scheme-less/relative URL inside `<>` is not an autolink and is not a valid HTML tag, so it falls through to escaped literal text. Absolute card URLs (the common case, e.g. `<https://example.com/...>`) are fine — this only bites relative/internal links, which do occur for Confluence smart links.

**Recommendation:** only use the `<url>` form when `url` looks like an absolute URI (has a `scheme:` prefix); otherwise fall back to `[url](url)` (the code already has that branch for "not clean" URLs — extend the condition). The same `<url>` branch is reused for url-only inline cards (`renderInlineCard`), so the fix covers both.

---

### 5. `multiBodiedExtension` / `extensionFrame` are unhandled (bodies lost)

**Location:** `internal/parser/AdfAstParser.java:154` default → `UnknownBlock`. `structure.md` lists `multiBodiedExtension` (top-level) and `extensionFrame` (child) as real nodes; the bundled draft-04 schema predates them so they have no definitions.

**Evidence (harness):**
```
multiBodiedExtension{...}  →  [Unsupported: multiBodiedExtension]
```
Degrading to a placeholder is acceptable as a *policy*, but the inner bodies (which can hold ordinary renderable content) are thrown away rather than salvaged. Contrast `bodiedExtension`, whose body *is* rendered (`MacroRenderer.renderBodiedExtension`).

**Recommendation:** at minimum, parse `multiBodiedExtension`/`extensionFrame` and render their child bodies (concatenate the frames) instead of discarding them, so content survives even when the macro chrome can't. Low urgency (rare nodes) but cheap and aligned with the "lossy but never silently empty" philosophy elsewhere.

---

## Low

### 6. Nested `taskList`/`decisionList` under a `listItem` are double-indented

**Location:** `internal/render/ListRenderer.java:179` `renderListItemBlock` special-cases only `BulletList`/`OrderedList` for tight, marker-aligned nesting; everything else takes the generic path (`AdfRenderer.renderBlock` with `listDepth+1`, then `indentLines(text, childIndent)`). A `decisionList`/`taskList` then gets **both** its own `listDepth`-based indent **and** `childIndent`.

**Evidence (harness):**
```
bulletList[ listItem[ "Outer", decisionList[ decisionItem(DECIDED "nested") ] ] ]
→  - Outer
   <blank>
       - [decision:DECIDED] nested     # 4 leading spaces; a nested bulletList would get 2
```
Still renders as a sub-list (4 ≥ 2, so not code/escape), so this is cosmetic, but it's inconsistent with `bulletList`/`orderedList` nesting and the extra blank line makes the parent list loose.

**Recommendation:** give `TaskList`/`DecisionList` the same marker-aligned treatment in `renderListItemBlock` (render with `parentIndent = childIndent` and without an additional depth-based indent), or render them through a shared "nested sub-list" path.

---

### 7. `toc` macro links to a slug the heading never gets (inline card in a heading)

**Location:** `internal/render/AdfHeadingCollector.java:129` — `extractHeadingPlainText` builds the slug source from an `inlineCard` using `card.attrs().url()`. But `CardRenderer.renderCardLink` *renders* a card with a title as `[title](url)` (title-first precedence). So the slug is built from the URL while the heading visibly renders the title. The renderer injects an explicit `<a id>` only for explicit Confluence anchors (`AdfRenderer.java:287`); auto-slug headings rely on the host (or the bundled `commonmark`) to regenerate the id from the rendered text, and `MacroRenderer.renderTocMacro` links to the collector's slug.

**Evidence (harness — heading `Ticket ` + inlineCard{title:"Bug 7", url:"https://ex.com/9"} with a `toc` macro above it):**
```
TOC link emitted by adf4j:          [Ticket https://ex.com/9](#ticket-httpsexcom9)
Heading's actual anchor (bundled commonmark renders the card as its title):
                                    <h1 id="ticket-bug-7">Ticket <a href="https://ex.com/9">Bug 7</a></h1>
```
`#ticket-httpsexcom9` ≠ `ticket-bug-7` → **the TOC link is dead**, and this is provable against the project's *own* parser, not just a hypothetical GitHub. This is a genuine internal inconsistency, not a "maybe GitHub differs" guess — hence I'm raising it from Low to Low–Med.

**Scope (kept honest):** I verified the divergence is specific to **inline cards that carry a title**. For plain text, `link` marks, `status` (`[Done]` → slug `done` either way), `emoji`, `date`, and inline images (alt text — `# ![pic](…) Title` slugs to `pic-title` on both the collector and the bundled commonmark), the slug source and the rendered text agree, so those do **not** diverge. So my earlier draft's lumping of "status/image" into the divergence was an over-statement; the demonstrable bug is the card-with-title case.

**Recommendation:** make `extractHeadingPlainText` mirror `CardRenderer`'s title-first precedence for cards (use title when present, else URL) — or, more robustly, derive the heading slug from the actually-rendered heading Markdown so the two can never drift. Add a `toc` + card-in-heading fixture asserting the link target equals the heading's generated id.

---

### 8. `_` is over-escaped intra-word

**Location:** `internal/render/MarkdownText.java:117` escapes `_` unconditionally. CommonMark never treats an intra-word `_` as emphasis (it is both left- and right-flanking and surrounded by alphanumerics, so it can neither open nor close).

**Evidence (harness):** `use snake_case_name here` → `use snake\_case\_name here` (renders correctly, just noisily). `*` genuinely *does* need intra-word escaping (`a*b*c` is emphasis), so it should stay; `_` is the provably-unnecessary one.

**Recommendation:** optional. `docs/markdown-conversion.md` documents the unconditional escape as deliberate, so this is a readability-vs-safety tradeoff. If output cleanliness matters, skip `_` when both neighbors are word characters. Low priority.

---

## Nit

### 9. Non-`1` versions yield an empty body, not best-effort output

**Location:** `internal/engine/AdfParsingService.java:76` raises `UNSUPPORTED_VERSION`; `AdfPipeline.convert` (`:57`) then returns `new MarkdownResult("", …)`. A document that is structurally fine but stamped `version: 2` converts to an empty string (diagnostics aside). Given the library is otherwise aggressively lenient (drops stray children, never throws), hard-failing the whole document on a future version number is the odd one out and will silently empty real content the day ADF v2 ships. Consider: warn on unknown version but still render. Very low urgency.

---

## Test-suite & fixture critique (per the brief: assume nothing is correct)

The suite is unusually disciplined — a snapshot suite (`AdfSpecConversionTests`, auto-discovered `*.json`/`*.md` pairs), an **independent** structural oracle (`CommonMarkOracleTests`, which re-parses output with production's `commonmark` extension list and asserts tree shape — this is the right way to avoid "snapshot rot"), strict parser/validation tests, and option-matrix tests. Coverage of escaping, links, code spans, table fallbacks, and malformed input is excellent. Specific concerns:

1. **A test pins a behavior I believe is wrong (Finding 1).** `AdfHeadingCollectorTests` ("emphasis marks are removed before heading markdown is rendered") asserts the strip as intended. It should be flipped to assert emphasis is *preserved* on the render path.
2. **Real gaps where behavior is asserted by no `.md` snapshot:**
   - emphasized headings (Finding 1) — only JSON-only support fixtures touch it;
   - nested `taskList` (Finding 3) — `task-list.json` is flat only; `grep` confirms no fixture has two `taskList`s;
   - HTML-fallback cells whose text begins with a block marker (Finding 2);
   - scheme-less/relative card URLs (Finding 4) — `block-card-url`/`internal-page-links` only use absolute URLs.
3. **`media-group` snapshot encodes a debatable choice.** `media-group.md` joins images with a single `\n` (soft break), so two images render space-separated on one line. Documented as "one visual cluster," but many consumers would expect one image per line (`  \n` or a blank line). Worth a conscious confirmation rather than a passive snapshot — flagging because the test silently blesses it.
4. **`discoverable_spec_inputs_do_not_duplicate_the_same_adf_payload`** keys de-dup on `JsonNode.equals`, which is order-sensitive; semantically-identical payloads with reordered keys would slip through. Minor.
5. **No round-trip / idempotence or large-fuzz testing.** The malformed suite is good for "doesn't throw," but there's no property test that, e.g., output re-parsed by commonmark never contains an element type the cell/heading shouldn't (which would have caught Finding 2 automatically). The oracle pattern is already there — extending it to tables/headings would be high-leverage.

---

## Node / mark completeness matrix (vs. schema + `structure.md`)

Coverage is broad. Handled correctly unless noted.

**Block nodes:** `doc, paragraph, heading, blockquote, codeBlock, panel, rule, bulletList, orderedList, listItem, taskList, taskItem, blockTaskItem, decisionList, decisionItem, table, tableRow, tableCell, tableHeader, mediaSingle, mediaGroup, media, caption, expand, nestedExpand, layoutSection, layoutColumn, extension, bodiedExtension, inlineExtension, syncBlock, bodiedSyncBlock, blockCard, embedCard` — all parsed/rendered. **Gaps:** `multiBodiedExtension`, `extensionFrame` → placeholder (Finding 5). Nested `taskList` dropped (Finding 3).

**Inline nodes:** `text, hardBreak, inlineCard, embedCard(inline), blockCard(inline), mediaInline, date, emoji, mention, placeholder, status, inlineExtension` — all handled, with sensible fallbacks (`emoji` id-only → empty, `status` blank → `[status]`, `mention` text→`@id`→`@mention`, `date` non-numeric passthrough) all verified.

**Marks:** `strong, em, code, strike, underline, subsup, link` rendered; `textColor, backgroundColor, border, fontSize, alignment, indentation` dropped (documented); `annotation, breakout, fragment, dataConsumer` passthrough; unknown → `UnknownMark`. Consistent with `docs/markdown-conversion.md`. Caveat: emphasis stripped specifically inside headings (Finding 1).

---

## Things that are correct (spot-checks worth recording, so they aren't "fixed" by accident)

- **Literal text is treated as literal.** `AT&amp;T` (literal ampersand-a-m-p) → `AT\&amp;T`, which round-trips back to `AT&amp;T` in HTML. Escaping `&`/`<` is right, not a bug.
- **`status` + following `(...)` cannot form a spurious link** — the *following text node* escapes its parens (`[Done]\(not a link\)`); the status placeholder itself need not be escaped.
- **Code-fence and code-span backtick collisions** grow the fence correctly (`code-fence-collision`, `code-span-backtick`).
- **URL destinations** are angle-wrapped on spaces/unbalanced parens and percent-encoded when angle-wrapping is unavailable; link-on-whitespace with `]` in the href stays a parseable anchor (oracle-verified).
- **GFM-table cell pipes** are escaped (`x|y` → `x\|y`) and survive GFM parsing.
- **Table HTML-fallback matrix** (number column, colspan/rowspan, non-GFM cell content) matches the documentation precisely.
- **Adjacent same-mark runs are coalesced** (`**Hel**`+`**lo**` → `**Hello**`) and even adjacent *overlapping* different-mark runs parse correctly (`**aa*****bb***` → `<strong>aa</strong><em><strong>bb</strong></em>`).
- **`taskItem` with an internal `hardBreak`** is fine: the `atLineStart` flag resets after a hard break (`AdfRenderer.java:198`), so a second line starting with `#`/`-` is escaped (`- [ ] line one  \n\# two` → the `# two` stays literal inside the item), and lazy continuation keeps it in the item. (Initially suspected, then ruled out.)
- **HTML-fragment table cells with a hard break do *not* double up `<br>`** (initially suspected): inside a table the hard break renders as a single `\n` (not `  \n`), so the `<p>`-unwrap + `\n`→`<br>` yields one `<br>`. Confirmed `a<br>b`, not `a<br><br>b`.
- **The renderer's `switch` statements are exhaustive at compile time.** `AdfBlock`, `AdfInline`, and `AdfMark` are all `sealed`, so an unrepresentable node can never reach the renderer and there is no `MatchException`/`default`-fallthrough risk — unknown *ADF* types are funnelled to `UnknownBlock`/`UnknownInline`/`UnknownMark` at parse time and handled by policy. This is a real robustness win and underpins the clean malformed-input behavior.

---

## Suggested priority order

1. **Finding 1** (heading emphasis) — highest user-visible fidelity loss, trivial fix, but requires flipping a test.
2. **Finding 2** (HTML cell block-promotion) — real content corruption on a default path; fix in the table/HTML-fragment boundary.
3. **Finding 3** (nested task lists) — schema-valid input silently lost; small parser+renderer change.
4. **Findings 4 & 7** — link/anchor fidelity (both produce a non-working link), cheap.
5. **Findings 5, 6, 8, 9** — completeness/polish.

For 1–4/7, the existing oracle-test pattern is the right home for regression coverage; consider a small property test that asserts "rendered table cells / headings never re-parse into block elements they shouldn't contain" (catches Findings 1 and 2 mechanically) and "every `toc` link target equals some heading's generated id" (catches Finding 7 mechanically).
