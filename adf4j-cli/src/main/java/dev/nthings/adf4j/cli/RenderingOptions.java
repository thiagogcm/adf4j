package dev.nthings.adf4j.cli;

import org.aesh.command.option.Option;
import org.jspecify.annotations.Nullable;

/// Markdown rendering options shared by `convert` and `analyze`, composed via `@Mixin`.
/// A `null` value keeps the library default, so the CLI never overrides what it was not asked to.
final class RenderingOptions {

  @Option(shortName = 't', name = "title", description = "Prepend TITLE as a level-1 (#) heading")
  @Nullable String title;

  @Option(
      shortName = 'c',
      name = "collapse-hard-breaks",
      hasValue = false,
      description = "Render hard breaks as soft breaks")
  boolean collapseHardBreaks;

  @Option(
      shortName = 'p',
      name = "escape-parentheses",
      hasValue = false,
      description = "Backslash-escape literal ( and )")
  boolean escapeParentheses;

  @Option(
      name = "image-size",
      hasValue = false,
      description = "Emit non-GFM image {width= height=} attributes")
  boolean imageSize;

  @Option(
      name = "html-visual-marks",
      hasValue = false,
      description = "Keep visual-only marks as inline <span style>")
  boolean htmlVisualMarks;

  @Option(
      name = "unknown-nodes",
      description = "How to render unknown nodes (default: placeholder)",
      allowedValues = {"placeholder", "skip", "fail", "preserve-raw"})
  @Nullable String unknownNodes;

  @Option(
      name = "table-fallback",
      description = "Table shape for non-GFM tables (default: gfm-promote-first-row)",
      allowedValues = {"gfm-promote-first-row", "gfm-empty-header", "html"})
  @Nullable String tableFallback;
}
