---
title: "adf4j"
description: "Java library and CLI for converting Atlassian Document Format (ADF) JSON to GitHub-Flavored Markdown (GFM)"
tags: [ "Java", "ADF", "Markdown" ]
---

## What it does

At its core, adf4j turns ADF documents into clean Markdown. Headings become `#` headings, panels become GFM alerts, lists become `-` bullets, links render inline, tables render as GFM pipe tables (or HTML when they are too complex), and so on. It also exposes the document's structure: references to other pages, attachments, mentions, heading outlines, and excerpt regions, so you can reason about a document before rendering it.

The library offers three main operations through a single entry point, `AdfToMarkdown`:

- **convert:** ADF JSON → GFM Markdown body, plus metadata and diagnostics.
- **analyze:** parse and inspect the document to extract references, attachments, and the heading outline without rendering.
- **parse / validate:** parse ADF JSON into a typed AST and report structural problems.

## Key design choices

**I/O-free and deterministic:** adf4j does no network calls, file reads, or database access. ADF references pages, attachments, and media by ID, so the library delegates every environment-specific lookup back to the caller through small resolver hooks: `MediaResolver`, `AttachmentResolver`, `PageLinkResolver`, `PageTreeResolver`, `ExcerptResolver`, and `ExtensionRenderer`. This keeps the core engine testable, sandbox-friendly, and independent of any particular Confluence instance or storage backend.

**Three-phase pipeline:** Every conversion runs parse → analyze → render. Analysis runs before rendering so the renderer has the complete heading outline up front, which is needed for table-of-contents macros and globally unique anchors. The same metadata produced during analysis is available on its own through `analyze()`.

**Immutable and thread-safe:** `AdfToMarkdown` compiles its internal pipeline once and can be reused across documents and threads. Per-document variation is expressed through immutable `MarkdownOptions` passed per call, not by rebuilding the converter.

**Safety by default:** ADF is treated as untrusted input. JSON nesting is bounded, link and media destinations are scheme-sanitized against an allow-list to block `javascript:` and similar schemes, and caller-provided callbacks are guarded so a single failing resolver cannot abort a whole document.

**Forward-compatible AST:** The parser preserves unknown ADF node types as raw JSON rather than dropping them. The renderer then handles them according to a configurable `UnknownNodePolicy` — placeholder, skip, preserve raw, or fail.

## How to use it

As a Java library, the typical usage is:

```java
AdfToMarkdown converter = AdfToMarkdown.create();
String markdown = converter.toMarkdown(adfJson);
```

For richer results:

```java
MarkdownResult result = converter.convert(adfJson);
String body = result.body();
ContentMetadata metadata = result.metadata();
boolean lossy = result.wasLossy();
```

The CLI ships as a GraalVM native executable and supports `convert`, `analyze`, and `validate` subcommands, reading from a file or stdin and writing to stdout or a file.

## Technical snapshot

- **Language / runtime:** Java 25, built as a Maven multi-module project.
- **Module system:** The library is a JPMS module (`dev.nthings.adf4j`); only the intended public packages are exported, everything under `internal.*` is encapsulated.
- **Modules:** `adf4j-lib` (the library), `adf4j-cli` (command-line wrapper), and an optional `adf4j-wasm` module for a WebAssembly build.
- **Dependencies:** deliberately small — Jackson for JSON, CommonMark + GFM extensions, jsoup for HTML table fallback, JSpecify for nullness annotations, and SLF4J for logging.

In short, adf4j is a focused, embeddable converter that brings Confluence/Jira rich content into Markdown-based workflows while giving callers full control over how IDs and macros are resolved.
