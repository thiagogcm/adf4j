package dev.nthings.adf4j.options;

/// Selects how a GFM-expressible table that lacks an all-`tableHeader` first row is rendered. A
/// table that genuinely cannot be a GFM table (number column, colspan/rowspan, non-GFM cell
/// content) always falls back to raw HTML regardless of this setting.
public enum TableFallback {
  /// Emit raw `<table>` HTML instead of promoting or synthesizing a header row.
  HTML,
  /// Promote the first row to the header row so the table renders as GFM Markdown.
  GFM_PROMOTE_FIRST_ROW,
  /// Default: prepend an empty header, keeping every original row as data.
  GFM_EMPTY_HEADER
}
