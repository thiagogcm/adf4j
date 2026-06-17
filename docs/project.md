---
title: "adf4j"
description: "Java library and CLI for converting Atlassian Document Format (ADF) JSON to GitHub-Flavored Markdown (GFM)"
tags: [ "Java", "ADF", "Markdown" ]
---

## What it does

adf4j converts Atlassian Document Format (ADF) JSON to GitHub-Flavored Markdown. It also extracts references, attachments, mentions, excerpts, and heading outlines so callers can inspect a document before rendering it.

The public entry point is `AdfToMarkdown`:

- `toMarkdown(json)` returns only the Markdown body.
- `convert(json)` returns the body, metadata, diagnostics, and unresolved references.
- `analyze(json)` returns metadata without rendering.
- `parse(json)` validates the root and returns the typed AST.

## Design choices

**No I/O:** ADF stores many resources by ID, not URL. adf4j never fetches those resources itself. Callers provide media, attachment, page, page-tree, excerpt, and extension resolvers when they want environment-specific output.

**Three phases:** Conversion runs parse, analyze, then render. Analysis builds the heading outline and metadata before rendering, which lets table-of-contents macros and unique heading anchors work in a single forward render pass.

**Reusable converter:** `AdfToMarkdown` is immutable and thread-safe. Create one instance and pass per-document `MarkdownOptions` when a page needs different resolver state.

**Safe defaults:** Input is treated as untrusted. JSON depth is bounded, URL schemes are sanitized, and resolver failures fall back to placeholders instead of aborting the whole document.

**Forward-compatible AST:** Unknown nodes are preserved as raw JSON in the AST. Rendering behavior is controlled by `UnknownNodePolicy`: placeholder, skip, preserve raw, or fail.

## Example

```java
AdfToMarkdown converter = AdfToMarkdown.create();
String markdown = converter.toMarkdown(adfJson);
```

For metadata and diagnostics:

```java
MarkdownResult result = converter.convert(adfJson);
String body = result.body();
ContentMetadata metadata = result.metadata();
boolean lossy = result.wasLossy();
```

## Technical snapshot

- **Runtime:** Java 25.
- **Build:** Maven multi-module project.
- **Module:** JPMS module `dev.nthings.adf4j`.
- **Artifacts:** `adf4j-lib`, `adf4j-cli`, and optional `adf4j-wasm`.
- **Dependencies:** Jackson, CommonMark with GFM extensions, jsoup, JSpecify, and SLF4J.

adf4j is a focused converter for Markdown-based workflows that need control over Confluence and Jira IDs, links, attachments, and macros.
