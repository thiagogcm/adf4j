## Feedback from building an ADF → Markdown export pipeline

**Context for the maintainer:** I'm using adf4j to convert Confluence Cloud `atlas_doc_format` page bodies into GitHub-Flavored Markdown for an offline, file-based archive. Alongside each page I download its attachments to local files and want the Markdown to reference those local files (and ideally other exported pages) rather than `atlassian.net` URLs. The notes below are friction points and feature requests from that integration, ranked by impact.

### 1. Add an `AttachmentResolver` hook, symmetric to `MediaResolver` (highest impact)

`MediaResolver` is a clean seam for `media`/`mediaInline` nodes — I give it a closure and every embedded file resolves to a local path. But Confluence `attachment:` "view file" macros don't go through any equivalent hook: they render to a hardcoded `[label](attachment:<fileId>)` destination. To localize those, I have to post-process the rendered Markdown string and replace the `attachment:<fileId>` scheme myself, which is fragile and couples me to that exact output format.

The `ExtensionRenderer` hook looks like it could intercept the macro, but as far as I can tell the `ExtensionContext` it receives only carries the raw macro parameters (e.g. the attachment `name`), not the resolved `fileId`/`AttachmentReference` that the built-in renderer computes. So using it would mean re-implementing adf4j's own attachment-reference resolution (the title→fileId lookup with fallbacks) on my side.

**Request:** a first-class `AttachmentResolver` (given the resolved attachment reference / fileId, return a URL or path), consulted the same way `MediaResolver` is. That would let consumers localize attachment macros without string surgery.

### 2. Add a `PageLinkResolver` for inter-page links

This is the biggest *fidelity* gap for an export use case. `ContentMetadata` exposes the outbound `pageRefs` (page node ids), and the library clearly already parses Confluence page URLs internally to populate them — but there's no hook to *rewrite* an inline page link to a caller-supplied destination. So links between two exported pages still point at the live site instead of the sibling file on disk.

**Request:** a `PageLinkResolver(pageNodeId → url)` mirroring `MediaResolver`, so a consumer that knows where each page landed can produce a fully cross-linked offline copy.

### 3. Make the converter reusable with per-call options

`AdfToMarkdown.with(options)` appears to construct a fresh pipeline (JSON mapper, CommonMark renderer, renderer objects) on each call, yet the underlying pipeline looks options-independent — the pipeline's own `convert` method already takes the options as a parameter. My options vary per document (each page needs its own attachment/media context), so I'm forced to rebuild that heavyweight pipeline on a hot path, which is at odds with the "configure once and reuse" guidance in the class docs.

**Request:** expose a public `convert(String adfJson, MarkdownOptions options)` overload (or a reusable pipeline/facade) so callers can build the converter once and pass document-scoped options per call.

### 4. Add a lossless policy for unknown nodes

The default `UnknownNodePolicy.PLACEHOLDER` silently drops content for unknown/future ADF node types. For an archival consumer that's data loss on schema evolution.

**Request:** a `PRESERVE_RAW`-style policy that emits the node's original JSON (in a fenced block or HTML comment) so exports stay forward-compatible and round-trippable.

### 5. Surface "what the body actually references" and conversion lossiness

Two smaller asks around `ContentMetadata`/`MarkdownResult`:

- I can't cheaply distinguish media/attachments the body *embeds* from files merely *attached* to the page, so a downloading consumer ends up fetching every attachment regardless of use. A reliable set of fileIds actually referenced by the rendered body would let consumers skip unreferenced binaries.
- `MarkdownResult.diagnostics()` is useful but uncategorized. A simple severity flag or a `wasLossy()` convenience on the result would let consumers programmatically flag documents that didn't convert cleanly, instead of inspecting every issue.
