package dev.nthings.adf4j.options;

/// How a table without an all-`tableHeader` first row renders. Tables GFM cannot express (number
/// columns, colspan/rowspan, non-GFM cell content) are always HTML.
public enum TableFallback {
  /// Raw `<table>` HTML; no header is added or promoted.
  HTML,
  GFM_PROMOTE_FIRST_ROW,
  /// Default. Prepends an empty header; every original row stays data.
  GFM_EMPTY_HEADER
}
