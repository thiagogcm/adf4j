# adf4j-lib — Audit Review

**Scope:** `adf4j-lib/src/main/java/dev/nthings/adf4j` (~6,000 LOC main, ~4,100 LOC test). Library only — the CLI module was out of scope except where it touches the library hot path. **Subject under audit:** an Atlassian Document Format (ADF) JSON → GitHub-Flavored-Markdown converter (Java 25, Jackson 3.x `tools.jackson`, commonmark 0.28.0, jsoup 1.22.2).

**Method:** Five independent specialist reviewers were run concurrently, each with a distinct point of view — (1) public-API & contract design, (2) conversion correctness & edge cases, (3) application security (hostile-input threat model), (4) JVM performance & concurrency, (5) maintainability & test coverage. Each reviewer was instructed to report *only* concerns and to verify every claim against the actual code; several findings were validated end-to-end through the project's own CommonMark "oracle" test and by empirical reproduction (StackOverflow at depth, the `Long.MIN_VALUE` date crash). The highest-severity items were additionally re-confirmed by the lead before publication.

This document lists **only the concerns and issues discovered**. Findings are tagged by reviewer lens — `A` (API), `C` (Correctness), `S` (Security), `P` (Performance), `M` (Maintainability) — and ordered by severity within each section. Cross-references link findings that share a root cause across lenses.

---

## Summary

| Severity | Count | Finding IDs |
|----------|-------|-------------|
| Critical | 0 | — |
| High | 9 | A1, C1, C2, C3, C4, S1, S2, M1, M2 |
| Medium | 18 | A2, A3, C5, C6, C7, S3, S4, S5, S6, P1, P2, M3, M4, M5, M6, M8, M9, M13 |
| Low | 23 | A4, A5, A6, A7, C8, C9, C10, S7, S8, P3, P4, P5, P6, P7, P8, P9, M7, M10, M11, M12, M14, M15, M16 |
| **Total** | **50** | |

---

## Cross-cutting themes

These root causes surfaced independently in more than one lens and are the highest-leverage areas to address first.

1. **Untrusted strings reach output sinks without scheme/inline escaping.** The single escape helper `MarkdownText.escapeUrlDestination` performs *no URL-scheme validation*, and the commonmark HTML renderer used for the table fallback is built with `sanitizeUrls(false)`. This is simultaneously a **security** issue (`S1` `javascript:`/`data:` injection, `S4` second-order injection via macros/resolvers, `S5` CSS injection) and a **correctness** issue (`C1` iframe URL, `C2` TOC anchor, `C3` card label all break valid links because they bypass escaping). One hardened URL/label-emission path would close most of both columns.

2. **Unbounded recursion with a too-high guard.** The parser and renderer recurse per nesting level; the only protection is Jackson's `maxNestingDepth(1000)`, which is far above the JVM stack budget. This is a confirmed remote DoS (`S2` — ~495 nested blockquotes overflow the default stack, ~200 at `-Xss256k`) and overlaps the performance recursion concerns (`P3`, `P7`).

3. **Caller-supplied callbacks are invoked without isolation.** The four SPI callbacks (`MediaResolver`, `AttachmentResolver`, `PageLinkResolver`, `ExtensionRenderer`) are called with no try/catch, so a single throw aborts the whole conversion. Flagged as both a **security/availability** issue (`S3`) and a **testing/contract** gap (`M2`) — and the library's documented "lossy but never fails" promise does not hold at the render phase.

4. **Inconsistent failure modes across the public surface.** Bad input variously yields a diagnostic-in-result, a silent empty result, a thrown `IllegalStateException`, or a propagated callback exception (`M13`), compounded by null-handling asymmetry on the `AdfDocument` overloads (`A3`) and a result record that breaks the defensive-copy contract its siblings uphold (`A1`).

5. **Coverage instruments exist but do not run.** jacoco is configured but bound to `verify`, while CI runs `clean test`, so no coverage ever executes or gates (`M1`); adversarial paths (callback failure, concurrency, malformed input, most AST records, the inline `FAIL` policy) are largely untested (`M2`–`M7`).

---

## API & Contract (A)

**[A1] `ParseResult` breaks the defensive-copy / null-guard contract every sibling record upholds** — Severity: High
- Location: `result/ParseResult.java:7`
- `public record ParseResult(AdfDocument document, List<ParseIssue> issues, boolean validAdfRoot)` has **no compact constructor**, unlike `MarkdownResult` (`result/MarkdownResult.java:10-14`) and `ContentMetadata` (`metadata/ContentMetadata.java:28-34`), which both null-coalesce and `List.copyOf(...)`. It is returned from the public `AdfToMarkdown.parse(String)`. Today's internal callers happen to pass immutable lists, so live instances are safe, but the public canonical constructor guarantees nothing: `new ParseResult(doc, null, true)` yields an instance whose `issues()` is `null`, NPE-ing any `result.issues().stream()`, and a caller-supplied mutable list is retained without copy.
- Why it matters: latent NPE and a leaked-mutability inconsistency on a public return type that diverges from its two sibling records.
- Fix: add a compact constructor `issues = issues == null ? List.of() : List.copyOf(issues);`.

**[A2] `MarkdownOptions` throws a bare NPE on a null element in `extensionRenderers`, undocumented** — Severity: Medium
- Location: `options/MarkdownOptions.java:45`
- The compact constructor does `List.copyOf(extensionRenderers)`, which rejects null *elements* with a JDK-internal NPE ("element cannot be null") that names no field. The class Javadoc documents the null-*list* case and resolver nullability but never that a null renderer element is fatal — so `builder().extensionRenderers(Arrays.asList(r, null)).build()` throws opaquely.
- Why it matters: a consumer assembling renderers conditionally gets a raw NPE from `build()` with no actionable message.
- Fix: validate/filter elements with a message naming `extensionRenderers`, or document the constraint.

**[A3] Null-input behavior of the `AdfDocument` overloads is silent, undocumented, and asymmetric with the `String` overloads** — Severity: Medium
- Location: `AdfToMarkdown.java:66-67,81-83,95-97`; `internal/engine/AdfPipeline.java:61-66,80-88`
- `convert(String)` / `analyze(String)` document "blank or invalid → empty". The `AdfDocument` overloads silently return `ContentMetadata.empty()` / an empty-body `MarkdownResult` for a **null** document with no Javadoc, while their `perCallOptions` argument *is* null-checked via `Objects.requireNonNull`. So `convert(null)` (document) returns empty, but `convert(doc, null)` (options) throws — opposite reactions to null on the same method family.
- Why it matters: a consumer cannot tell whether a null document is a programming error or a defined empty result.
- Fix: document the null-document behavior on the `AdfDocument` overloads (or make it consistent with the null-`String` path).

**[A4] `Attributes` advertises "immutable" but `Map.copyOf` is shallow** — Severity: Low
- Location: `ast/Attributes.java:13-19`
- The Javadoc calls the view "immutable" and the constructor does `Map.copyOf(values)`, which is shallow. Library-built instances are deeply immutable (the parser produces immutable nested collections at `internal/parser/AdfAstParser.java:460,468`), but a consumer constructing `new Attributes(map)` with a nested mutable `List`/`Map` keeps a live reference reachable via `values()`.
- Why it matters: the documented "immutable" guarantee holds only for library-built instances.
- Fix: deep-copy in the constructor, or soften the Javadoc to "shallow-immutable; supply already-immutable nested values."

**[A5] Exported sealed hierarchies make the entire node taxonomy part of the SemVer surface** — Severity: Low
- Location: `ast/AdfNode.java:3`, `ast/AdfBlock.java:3-37`, `ast/AdfInline.java:3-14`, `ast/AdfMark.java:3-24`; exported at `module-info.java:16`
- These sealed types are exported and consumers are encouraged to `switch` over them. Adding a new node to a `permits` clause is source- and binary-incompatible for any downstream exhaustive switch lacking a `default`; removing/renaming a permitted type breaks them outright.
- Why it matters: every future ADF construct modeled as a first-class node is a potential breaking change for consumers.
- Fix: document that permits lists may grow in minor versions and advise a `default`/`UnknownBlock` arm, or expose a narrower visitor instead of the raw sealed hierarchy.

**[A6] Public API records ship with no Javadoc, including ones with non-obvious semantics** — Severity: Low
- Location: `result/ParseResult.java` (entire), all of `metadata/*Reference.java`, most of `ast/*` (e.g. `Status`, `Emoji`, `MediaAttrs`)
- `ParseResult` is a documented return type yet has no docs — notably `validAdfRoot`, whose "best-effort still valid" semantics are surprising: the parser treats `UNSUPPORTED_VERSION` as non-fatal and still sets `validAdfRoot=true` (`internal/parser/AdfParsingService.java:54-60`). `MediaAttrs` exposes 12 undocumented string components (ids vs URLs vs MIME).
- Why it matters: consumers reverse-engineer field meaning from names and will misread `validAdfRoot` as strict validity.
- Fix: add component-level Javadoc, prioritizing `ParseResult.validAdfRoot` and `MediaAttrs`.

**[A7] `Adf.toMarkdown` is a one-way door with respect to configuration** — Severity: Low
- Location: `Adf.java:6,10`; `AdfToMarkdown.java:100`
- `Adf.toMarkdown(String)` delegates to a static `SHARED = AdfToMarkdown.create()` bound to `MarkdownOptions.defaults()` forever, with no options overload, yet shares its name with `AdfToMarkdown.toMarkdown` which *does* have a `(String, MarkdownOptions)` sibling. A consumer who starts on the `Adf` facade and later needs options has no incremental path.
- Why it matters: identically-named entry points imply an interchangeability they don't have.
- Fix: document on `Adf` that it is defaults-only and point to `AdfToMarkdown` for configuration.

---

## Conversion Correctness & Edge Cases (C)

> Items marked "verified" were reproduced end-to-end through the project's CommonMark oracle.

**[C1] iframe macro emits an unescaped URL destination** — Severity: High *(also a security sink — see S4)*
- Location: `internal/render/MacroRenderer.java:211`
- `renderIframeMacro` returns `"[Embedded content](%s)".formatted(src)` with raw `src`. Any space/paren/angle bracket breaks the link. Verified: `src = "https://e.com/a (b) c"` → oracle output `<p>[Embedded content](https://e.com/a (b) c)</p>` — no anchor produced. Every other link site routes through `escapeUrlDestination`; this one does not.
- Fix: wrap with `MarkdownText.escapeUrlDestination(src)`.

**[C2] TOC link anchor href is not URL-escaped** — Severity: High
- Location: `internal/render/MacroRenderer.java:191`
- `"- [" + label + "](#" + heading.anchor() + ")"` escapes the label but emits the `#anchor` raw. For an explicit Confluence anchor id like `a b)c`, the entry becomes `[H](#a b)c)` → verified oracle output `<li>[H](#a b)c)</li>` with no link. Commonmark-derived slugs are clean, but explicit ids are arbitrary attribute strings.
- Fix: run the fragment through `MarkdownText.escapeUrlDestination("#" + heading.anchor())` (or percent-encode it).

**[C3] url-only card label is not inline-escaped on the non-autolink path** — Severity: High
- Location: `internal/render/CardRenderer.java:61`
- When a url-only card can't be an autolink (relative or rewritten page link), the raw `url` is used as the visible label without escaping. Verified: `/wiki/a*b*c` → `[/wiki/a*b*c](/wiki/a*b*c)`, label renders as `/wiki/a<em>b</em>c`; `/wiki/a]b` → no anchor at all (the `]` truncates the label).
- Fix: pass the label through `MarkdownText.escapeInlineText(url, false)`.

**[C4] Nested ordered list with `start ≠ 1` renders tight and is absorbed as paragraph text (silent structural loss)** — Severity: High
- Location: `internal/render/ListRenderer.java:177`, `isNestedListBlock` at `:187`
- All nested lists are kept tight; CommonMark only lets a list interrupt a paragraph when an ordered list starts at 1. Verified: `bulletList → listItem[paragraph "top", orderedList order=3 "n1"]` produces `- top\n  3. n1`, parsed as `<li>top 3. n1</li>` — the entire sublist is lost (no `<ol>`). A leading blank line yields the correct `<ol start="3">`. The existing `ordered-list-nested-indent` fixture only covers order=1, so this is untested.
- Fix: in the continuation loop, treat a nested ordered list whose `order != 1` like a non-list block (emit the preceding blank line).

**[C5] Link title with an embedded newline is emitted raw** — Severity: Medium
- Location: `internal/render/TextMarkRenderer.java:217` (`escapeLinkTitle`)
- Only `\` and `"` are escaped; a newline in `title` is written literally, producing `[x](https://e.com "a⏎b")`. It survives current commonmark parsing but is fragile and not valid single-line GFM title form.
- Fix: collapse/encode line breaks (`\R` → space) in `escapeLinkTitle`.

**[C6] Default table fallback relabels a single data row as a header (silent data demotion)** — Severity: Medium
- Location: `internal/render/TableRenderer.java:49` (`GFM_PROMOTE_FIRST_ROW`, the default)
- A headerless table whose only row is data has that row promoted into the GFM header, leaving zero body rows. Verified: one-row table `x | y` → `<thead><th>x</th><th>y</th></thead>` with no `<tbody>` — genuine data misrepresented as column headings.
- Fix: when promoting and there is only one row (or generally for headerless data), prefer `SYNTHESIZE_EMPTY` so the data stays in the body.

**[C7] Empty/short table rows are dropped or padded, shifting columns silently** — Severity: Medium
- Location: `internal/render/TableRenderer.java:62-68` (empty rows `continue`d), `Row.withPadding` at `:217`
- A `tableRow` with no cells is skipped entirely and a short row is right-padded, so a ragged ADF table is coerced to a rectangle with no diagnostic. Verified: 2-col + empty + 1-col rows → the empty row vanishes and the 1-col row becomes `| c | |`. No lossiness signal is recorded.
- Fix: at minimum record a lossiness/parse diagnostic when a row is dropped or padded; consider routing ragged tables to the HTML fallback.

**[C8] Code-block language info string is not trimmed/validated** — Severity: Low
- Location: `internal/render/MarkdownText.java:37`; `AdfRenderer.renderCodeBlock` (only whole-fence `stripTrailing`)
- The `language` attr is concatenated verbatim: `"  js  "` yields `` ```  js `` (leading spaces kept). Backticks or spaces in the language would corrupt the fence/info string.
- Fix: `language.strip()` (and strip backticks) before building the fence.

**[C9] Unknown `subsup` subtype silently becomes subscript** — Severity: Low
- Location: `internal/render/TextMarkRenderer.java:183`
- `"sup".equalsIgnoreCase(subSup.subSupType()) ? "sup" : "sub"` maps any non-`sup` value — including garbage — to `<sub>`. Verified: subsup type `"foo"` → `<sub>x</sub>`.
- Fix: only emit `<sub>` when the type equals `sub`; otherwise leave the text unwrapped (or record lossiness).

**[C10] Empty GFM-alert panel renders an unrecognized alert** — Severity: Low
- Location: `internal/render/AdfRenderer.java:404-410` (`renderPanel`)
- A panel with no body emits just `> [!NOTE]`; GFM requires alert content, so the oracle parses it as a plain blockquote with literal text `<blockquote><p>[!NOTE]</p>`.
- Fix: when the body is blank, omit the panel or emit a blockquote with a placeholder line so the alert is well-formed.

---

## Security (S)

> Threat model: the ADF JSON input is hostile (e.g. attacker-authored Confluence/Jira content).

**[S1] `javascript:`/`data:`/`vbscript:` URLs emitted verbatim into links and the HTML-table fallback** — Severity: High
- Location: `internal/render/MarkdownText.java:198` (`escapeUrlDestination` — no scheme validation, confirmed); sinks at `TextMarkRenderer.java:94-98`, `CardRenderer.java:52-61`, `MediaRenderer.java:76-88`, `MacroRenderer.java:211,226`; HTML renderer built with `sanitizeUrls(false)` at `AdfRenderer.java:117` (confirmed)
- A link mark `{"href":"javascript:alert(document.cookie)"}` is emitted as `[label](javascript:...)`. In the HTML-table path the commonmark renderer is built with `sanitizeUrls(false)`, so the malicious href reaches the DOM directly; in the plain-markdown path it survives to any downstream renderer.
- Impact: stored XSS / script execution in any consumer that renders the output — the explicit threat model.
- Fix: allowlist schemes (http/https/mailto/relative) in `escapeUrlDestination`, replacing or stripping anything else, and set `sanitizeUrls(true)` on the HTML renderer.

**[S2] Unbounded parser/renderer recursion → uncaught `StackOverflowError` (DoS)** — Severity: High
- Location: parser recursion `internal/parser/AdfAstParser.java:135`; renderer recursion `internal/render/AdfRenderer.java:145`; only guard `MAX_NESTING_DEPTH = 1000` at `internal/parser/AdfParsingService.java:25` (confirmed)
- The 1000 JSON-nesting cap is far above the JVM stack budget for the recursive AST build and render. Verified: ~495 nested `blockquote`s overflow the default stack; ~200 at `-Xss256k`, ~300 at `-Xss512k`. `StackOverflowError` is an `Error`, so it is *not* caught by `catch (JacksonException)` in `AdfParsingService.parse` nor in the renderer — it kills the calling thread. A few-KB payload is a trivial remote DoS.
- Fix: lower `MAX_NESTING_DEPTH` to ~64–100 and/or track an explicit depth counter in `parseBlock`/`renderBlock` and degrade gracefully.

**[S3] Uncaught exceptions from user resolver/extension callbacks crash the conversion** — Severity: Medium *(also M2)*
- Location: `MediaRenderer.java:104`, `MacroRenderer.java:234`, `TextMarkRenderer.java:112`, `MacroRenderer.java:116`
- None of the four caller-supplied callbacks (`MediaResolver`, `AttachmentResolver`, `PageLinkResolver`, `ExtensionRenderer`) are wrapped in try/catch; a throw propagates out of `convert()`. With attacker-controlled input selecting which nodes hit a resolver, an attacker can steer execution into the throwing path.
- Impact: a single throwing/buggy callback aborts the entire document conversion (availability).
- Fix: wrap each callback in try/catch; log and fall back to the default/synthetic destination on exception.

**[S4] Second-order injection: resolver/extension return values bypass escaping** — Severity: Medium *(shares root cause with C1, S1)*
- Location: `MacroRenderer.java:116-121` (ExtensionRenderer output), `:211` (iframe `src`), `:234-239` (attachment), `MediaRenderer.java:104-107`, `TextMarkRenderer.java:112`
- `ExtensionRenderer.render(...)` output is inserted with **no escaping**. The `iframe` macro builds its link from raw attacker-controlled `macroParams` `src` (not even routed through `escapeUrlDestination`). Resolver-returned URLs go through `escapeUrlDestination`, which per S1 does not block dangerous schemes.
- Impact: markdown/HTML injection and `javascript:`-URL injection via macro params and extension output.
- Fix: route every macro-derived URL (esp. iframe `src`) through scheme-validating escaping; document that `ExtensionRenderer` output is trusted, or escape it.

**[S5] `htmlVisualMarks` emits raw `<span style>` / `<div align>` with attacker-controlled CSS** — Severity: Medium
- Location: `TextMarkRenderer.java:71-73,134-163`; `AdfRenderer.java:337`
- When `htmlVisualMarks` is enabled, `textColor`/`backgroundColor`/`fontSize`/`border` mark values are inserted into a `style` attribute. `escapeHtmlText` escapes `< > & "` (blocking attribute breakout) but performs no CSS sanitization, so `red;background:url(...)` injects extra declarations. In the HTML-table fallback these `<span>` fragments are re-parsed as raw HTML and preserved.
- Impact: CSS injection (data-exfil via `background:url`, UI redress) in consumers rendering the HTML. Lower than S1 (quote-breakout blocked; feature is opt-in).
- Fix: allowlist-validate color/size values (e.g. `#hex`/`rgb()`/keywords/`\d+px`) before emitting them.

**[S6] `date` timestamp `-9223372036854775808` throws an uncaught `DateTimeException` (DoS)** — Severity: Medium
- Location: `internal/AdfText.java:18-33` (`dateFromTimestamp`) — confirmed `catch` handles only `NumberFormatException`
- `Math.abs(Long.MIN_VALUE)` stays negative, so the `< 100_000_000_000L` branch calls `Instant.ofEpochSecond(Long.MIN_VALUE)`, which throws `DateTimeException`; the catch only handles `NumberFormatException`. Verified: one inline `date` node with this value crashes the whole conversion.
- Fix: also catch `DateTimeException` (or `RuntimeException`) and guard `Long.MIN_VALUE`.

**[S7] Catastrophic-backtracking risk in the Confluence page-URL regex (suspected ReDoS)** — Severity: Low
- Location: `internal/ConfluenceSupport.java:11-13`
- `^(?:https?://[^/]+)?(?:/wiki)?(?:/spaces/[^/]+)?/pages/(?:edit-v\d+/)?(\d+)(?:/[^?#]*)?(?:\?.*)?(?:#.*)?$` runs on every link/card href of unbounded length with no input cap; multiple optional leading groups plus `(\d+)` and `.*` tails create overlapping match possibilities. Exponential blowup was *not* reproduced in the time available (likely linear/quadratic), hence **suspected**.
- Fix: cap href length before matching; tighten the trailing `(?:\?.*)?(?:#.*)?` with atomic/possessive groups or `[^#]*`.

**[S8] Attacker-controlled identifiers/content logged at WARN/ERROR** — Severity: Low
- Location: `AdfParsingService.java:55`, `MacroRenderer.java:249-256`, `AdfRenderer.java:62,521,533`
- Extension keys, node types, and validation-issue messages (all attacker-influenced) are logged without stripping CR/LF or truncating, enabling log forging and volume-flooding.
- Fix: sanitize (strip CR/LF, truncate) attacker-derived values before logging and drop to DEBUG.

*Scope checked and ruled out:* no XML parsing anywhere → no XXE (jsoup only *builds* elements; the only `element.html()` re-parse consumes library-generated markup). Jackson is configured with `StreamReadConstraints` and uses `readTree` with no polymorphic typing → no deserialization-gadget risk (residual issue is the depth value itself, S2). Numeric attrs besides the date case use null-safe `asInt/asLong`. Plain-text HTML-fallback injection is blocked because `escapeInlineText` escapes `<`/`&` before the commonmark re-parse — the real exposure is the href/CSS paths (S1/S5).

---

## Performance & Concurrency (P)

> **Thread-safety verdict (verified, not a finding):** no data race exists in the library's own code. The shared singleton path (`Adf.SHARED` → one `AdfToMarkdown` → one `AdfPipeline` → one `AdfRenderer`) reuses only objects that are documented thread-safe (commonmark `Parser`/`HtmlRenderer`, the Jackson `JsonMapper` built once and never reconfigured) or stateless; all per-document mutable state (`RendererState`, `RenderContext`, `MacroDiagnostics`, analyze collectors) is freshly allocated per call. The findings below are allocation/efficiency plus one third-party global-lock hazard.

**[P1] `URLConnection.guessContentTypeFromName` on the per-node analyze hot path** — Severity: Medium
- Location: `internal/AttachmentReferences.java:108` (via `AdfContentMetadataExtractor.java:168` `inferMediaType` and `MediaRenderer.java:92` `isImage`)
- `guessContentTypeFromName` routes through `URLConnection.getFileNameMap()` / `sun.net.www.MimeTable`, a process-wide lazily-initialized table historically guarded by global synchronization — a contention point under concurrent high-volume conversion. Its result is then overridden by the local `MEDIA_TYPES_BY_EXTENSION` map for the cases that matter anyway.
- Fix: drop the `guessContentTypeFromName` call and resolve solely against `MEDIA_TYPES_BY_EXTENSION` (extend it if needed).

**[P2] `convert(String)` walks the tree twice — full analyze pass even when no metadata is consumed** — Severity: Medium
- Location: `internal/engine/AdfPipeline.java:89-90` (`render` always calls `analyzer.analyze(...)` then `renderer.render(...)`)
- Every `convert()` runs a complete `AdfNodeWalker` traversal (heading + metadata + lossiness collectors, including the P1 MIME guessing and per-node set/map accumulation) and then a second full render traversal. The one-liner `Adf.toMarkdown` / `AdfToMarkdown.toMarkdown` discards `MarkdownResult.metadata()`, so the metadata half is pure waste on that path; only the heading outline is needed by the renderer.
- Fix: when the caller needs only the body, run the heading collector alone and skip metadata/lossiness, or expose a body-only path.

**[P3] `renderBlocks` uses `Stream.mapMulti` + `toList` in the hot recursive path** — Severity: Low
- Location: `internal/render/AdfRenderer.java:193-195`
- Every block-list render (recursive for every container: lists, tables, panels, layouts, expands, blockquotes, doc root) allocates a stream, a `mapMulti` consumer lambda, and an intermediate immutable list where a pre-sized `ArrayList` + `for` loop would do.
- Fix: replace with a `for` loop accumulating into an `ArrayList`.

**[P4] `applyMarks` builds a `Function`-composition decorator chain per text run** — Severity: Low
- Location: `internal/render/TextMarkRenderer.java:45-81` (`markDecorator`)
- For every marked text inline it builds a list, sorts it, then composes a `Function<String,String>` via repeated `andThen(...)` (a capturing lambda + a composed `Function` per mark) — heavy short-lived allocation for the most frequent operation in text-heavy documents.
- Fix: apply marks directly in a loop instead of composing `Function` objects.

**[P5] `coalesceAdjacentText` allocates buffers unconditionally and compares mark sets via double `containsAll`** — Severity: Low
- Location: `internal/render/AdfRenderer.java:225-259` (`sameMarkSet` uses `containsAll` both ways); heading path double-allocates at `:360`
- A new `ArrayList` + `StringBuilder` run buffer is allocated for every inline list even when there is nothing to coalesce (single text node — the common case), and `sameMarkSet` does O(n·m) list scans per pair.
- Fix: fast-path return for 0/1 elements or no adjacent same-mark `Text`; compare mark sets by size + element-wise equality.

**[P6] `String.formatted(...)` used for trivial fixed-shape concatenations on hot paths** — Severity: Low
- Location: `MediaRenderer.java:80,88,117,119`; `TextMarkRenderer.java:97-98`; `CardRenderer.java:54,59-61`; `AdfRenderer.java:312`
- `"[%s](%s)".formatted(...)`, `"media:%s".formatted(id)`, etc. each parse a format string through `Formatter`, once per link/media/card/heading node.
- Fix: replace fixed-shape `formatted` calls with plain concatenation.

**[P7] Repeated join→split round-tripping of already-joined text in list/quote indentation** — Severity: Low
- Location: `ListRenderer.java:201,205,219-220,258`; `RenderBuffer.java:28-42`; `AdfRenderer.java:433` (`toBlockQuote`)
- Nested rendering repeatedly `joinBlocks(...)` into one big string then `splitLines(...)` back apart (each split via `LINE_BREAK_PATTERN` + a fresh list), once per nesting level — roughly O(depth × text-length) churn on deeply nested lists/quotes.
- Fix: thread `List<String>` lines through the indentation helpers instead of join→split at each level.

**[P8] Parser re-resolves `node.path("attrs")` once per field** — Severity: Low
- Location: `internal/parser/AdfAstParser.java:195-213` (Mention resolves it 5×, Status 4×, Emoji 3×; similar in `parseBlock`)
- Several parsers call `node.path("attrs")` repeatedly per node instead of hoisting it once (as `parseMark` already does).
- Fix: `var attrs = node.path("attrs");` once per branch and reuse.

**[P9] Per-call defensive copies of collections that are already effectively immutable** — Severity: Low
- Location: `confluence/ConfluenceRenderContext.java:46-53`; `ast/Attributes.java:18`; `ast/MacroParams.java:10`; `options/MarkdownOptions.java:45`
- `Attributes`/`MacroParams` call `Map.copyOf` in their canonical constructors, copying maps the parser just built and never shares — once per attrs/macroParams node during parse; `ConfluenceRenderContext` re-copies an already-immutable attachment map.
- Fix: wrap with `Collections.unmodifiableMap` (no copy) when the input is already a freshly built/immutable instance.

---

## Maintainability & Test Coverage (M)

**[M1] jacoco never runs in CI and no coverage gate is enforced** — Severity: High
- Location: parent `pom.xml` jacoco config (bound to `verify`); `.github/workflows/build-and-test.yml:32` runs `./mvnw clean test` (confirmed — stops before `verify`)
- jacoco's `report` is bound to `verify`, but CI runs `clean test`, so jacoco never executes, no report is produced, and there is no `check` goal or `<minimum>` threshold anywhere. The coverage configuration is inert.
- Fix: add a `jacoco:check` execution with a line/branch minimum and run `mvn verify` in CI.

**[M2] No resolver/extension callback failure is tested; throwing callbacks crash the conversion** — Severity: High *(same defect as S3)*
- Location: `MacroRenderer.java:116`, `MediaRenderer.java:104`, `TextMarkRenderer.java:112`, `MacroRenderer.java:234`; tests: zero (`grep assertThrows` over `src/test` = 0; no throwing-callback test)
- The four caller-supplied callbacks are invoked with no try/catch and no test exercises the failure path, so the documented "lossy but never fails" contract (`AdfParsingService` comment, line 24) is broken at the render phase and untested.
- Fix: wrap each callback in try/catch with placeholder fallback, and add throwing-callback tests.

**[M3] No failure-path coverage beyond two spots; no concurrency/property/fuzz tests** — Severity: Medium
- Location: cross-cutting (`src/test/java`)
- No concurrency tests (despite the documented thread-safety promise at `AdfPipeline.java:21`), no property/fuzz testing (no jqwik/quicktheories), and `assertThatThrownBy` appears only for `AdfToMarkdown.with(null)` and the `FAIL` block policy.
- Fix: add a parallel-conversion test and a property test feeding random/garbled JSON asserting "never throws."

**[M4] `UnknownNodePolicy.FAIL` inline path untested** — Severity: Medium
- Location: `AdfRenderer.java:549` (inline FAIL branch); test covers only the block branch (`AdfToMarkdownRenderingTests.java:82-88`)
- The structurally identical inline FAIL throw has no test; a regression there ships silently.
- Fix: add a FAIL test with an unknown inline node.

**[M5] 41 AST records implement defensive-copy/null-normalization but the contract test covers only 4** — Severity: Medium
- Location: `ast/AstRecordContractTests.java` (4 records) vs ~41 records with `List.copyOf`/null-normalization (Paragraph, Heading, Table, TableRow, TableCell, lists, Panel, Blockquote, Caption, Expand, Layout*, Media*, Link, Attributes, Text, …)
- The immutability guarantee (a real leaked-mutable-list bug class) is verified for <10% of the records that implement it.
- Fix: a parameterized contract test asserting, for every list-bearing record, that the returned list is unmodifiable and decoupled from the input.

**[M6] Spec golden harness silently ignores top-level fixtures; one fixture is orphaned** — Severity: Medium
- Location: `spec/AdfSpecConversionTests.java:103` (`getNameCount() > 1` restricts discovery to subdirectories)
- The 8 top-level `adf/spec/*.json` files are excluded from both the golden conversion test and the `every_spec_input_has_an_expected_markdown_file` guard; `internal-page-links.json` is referenced by no test at all (confirmed). The "every input has an expected output" invariant is enforced only for the subset the walk reaches.
- Fix: drop the `getNameCount() > 1` filter (or document the two tiers); delete or wire up `internal-page-links.json`.

**[M7] `bodiedExtension` / `bodiedSyncBlock` have parser cases but no JSON fixture** — Severity: Low
- Location: `AdfAstParser.java:170,174`; no fixture under `src/test/resources/adf` contains these types (only hand-built records in `AstRecordContractTests`)
- The end-to-end render of these block types (`MacroRenderer.headerThenBodies`) has no golden fixture.
- Fix: add spec fixtures for both.

**[M8] Block/inline node-type set is enumerated in 4 separate exhaustive switches that must stay in lockstep** — Severity: Medium
- Location: `AdfAstParser.parseBlock` (`:135`), `AdfRenderer.renderBlock` (`:146`), `AdfNodeWalker.block` (`:72`), `AdfContentMetadataExtractor`
- Every new ADF block type requires touching all four, and the walker/extractor each re-derive "what are a node's children" independently. Only the exhaustive `switch` (no default) catches an omission at compile time; the analyze extractor may not.
- Fix: add a `List<? extends AdfBlock> children()` (default empty) on the block hierarchy so walker/analyzer share one traversal.

**[M9] Node-type names and attr keys are bare string literals scattered across the parser** — Severity: Medium
- Location: `AdfAstParser.java` (`"attrs"` ×44, `"content"` ×32, `"type"` ×7, `"marks"` ×6, plus ~60 type-name literals); re-typed in `RootValidator`, `ConfluenceSupport`, fixtures
- `JsonFields` centralizes coercion but not the key/type-name vocabulary; a typo in any literal is a silent parse miss.
- Fix: a `NodeTypes` / `AttrKeys` constants holder (or enum) referenced by parser, validator, and analyze layer.

**[M10] `renderUnknownBlockByPolicy` and `renderUnknownInlineByPolicy` are near-identical copy-paste** — Severity: Low
- Location: `AdfRenderer.java:516-539` and `:541-562`
- Two ~24-line methods differ only in the yielded strings and the "block/inline" log word.
- Fix: extract a generic `<T> T applyUnknownPolicy(label, raw, skipVal, rawFn, placeholderFn)` helper.

**[M11] `MarkdownOptions` telescoping withers/builder — 10 fields re-listed across 12 call sites** — Severity: Low
- Location: `options/MarkdownOptions.java:64-126` (10 `withX` methods each re-passing all 10 positional args), plus `defaults()` and `Builder.build()`
- Adding an 11th option means editing 12 argument lists by hand; a misordered argument (there are already two `boolean` fields) compiles silently.
- Fix: implement each wither via `toBuilder()`/builder copy.

**[M12] `[%s](%s)` link assembly duplicated across 5 renderers** — Severity: Low
- Location: `CardRenderer.java:52-54`, `MediaRenderer.java:80`, `MacroRenderer.java:211,226`, `TextMarkRenderer.java:98`
- The "escape label + escape destination + format as markdown link" idiom is hand-rolled in 5 places (escaping is centralized, the assembly is not) — the same root cause that produced the escaping omissions in C1/C2/C3.
- Fix: a `MarkdownText.link(label, destination[, title])` builder that escapes both parts.

**[M13] Inconsistent failure modes across the public surface** — Severity: Medium
- Location: cross-cutting — `AdfParsingService.parse` (diagnostic in result), `AdfPipeline.convert` (silent empty result + diagnostics, `:73-76`), `UnknownNodePolicy.FAIL` (throws `IllegalStateException` mid-render), callbacks (throw uncaught — M2/S3)
- Four different reactions to bad input mean a maintainer cannot predict from a signature whether a malformation throws, returns empty, or returns a diagnostic.
- Fix: document the failure-mode matrix on the public API and converge (e.g. always diagnostics-in-result except the explicit FAIL opt-in).

**[M14] Overly broad `catch (RuntimeException)` masks a should-not-happen serialization failure** — Severity: Low
- Location: `AdfAstParser.java:490` (`rawJson` returns `"{}"` on any `RuntimeException`, no log)
- Re-serializing an in-memory `JsonNode` should never fail; swallowing all `RuntimeException` to `"{}"` hides genuine bugs, and `PRESERVE_RAW` then silently emits an empty object.
- Fix: catch the specific Jackson exception and at least `log.warn`.

**[M15] Resolver/callback return-type convention is inconsistent (Optional vs nullable)** — Severity: Low
- Location: `extension/ExtensionRenderer.java:13` (`Optional<String>`) vs `MediaResolver.java:13`, `AttachmentResolver.java:15`, `PageLinkResolver.java:15` (nullable `String`)
- Four caller-implemented SPI callbacks, one uses `Optional`, three use null-means-defer.
- Fix: pick one convention for all SPI callbacks.

**[M16] Javadoc references a docs file that does not exist** — Severity: Low
- Location: `AdfToMarkdown.java:22` — "documented in `docs/markdown-conversion.md`"; confirmed `docs/` contains only `spec/{README.md,structure.md,adf-schema.json}`
- The one pointer to per-option behavior on the primary entry-point class is a dead link.
- Fix: create the file or repoint to the in-code `MarkdownOptions` Javadoc.

*Checked and found clean (not findings):* no `TODO/FIXME/HACK`, no `System.out`/`printStackTrace`, no `@Deprecated`, no empty catch blocks, no commented-out code; SLF4J logging is parameterized (no string concatenation); the `internal` package boundary is correctly enforced by `module-info`; inline-punctuation escaping is centralized in `MarkdownText`/`HtmlFragments`; the empty `catch (NumberFormatException _)` sites (`AdfText:30`, `MediaRenderer:143`, `TocLevelRange:25`, `RootValidator:61`, `MacroRenderer:164`) are intentional, acceptable numeric fallbacks.

---

## Suggested remediation order

1. **Security-critical, small surface:** `S1` (URL scheme allowlist + `sanitizeUrls(true)`), `S2` (lower `MAX_NESTING_DEPTH` / add depth counter), `S6` (catch `DateTimeException`), `S3` (wrap callbacks) — these are the genuine remote-input hazards and most are a few lines each.
2. **Correctness fixes that share the S1 escaping fix:** `C1`, `C2`, `C3` (route every label/destination through escaping — ideally via the single `MarkdownText.link(...)` helper proposed in `M12`), then `C4` (nested ordered-list blank line) and `C6`/`C7` (table data demotion / dropped rows).
3. **Contract hardening:** `A1` (`ParseResult` compact constructor), `A3` (document null-document behavior), `M13` (failure-mode matrix).
4. **Test & CI gaps:** `M1` (run jacoco with a gate in CI), `M2`/`M3` (callback-failure, concurrency, and property tests), `M5`/`M6` (record-contract and golden-fixture coverage).
5. **Maintainability & performance:** consolidate the duplicated switches/literals/link-assembly (`M8`, `M9`, `M10`, `M12`), then the per-call/per-node allocation cleanups (`P1`, `P2`, `P4`).
