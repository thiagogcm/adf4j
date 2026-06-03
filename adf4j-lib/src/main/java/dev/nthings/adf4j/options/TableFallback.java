package dev.nthings.adf4j.options;

/**
 * Controls how a GFM-safe table that lacks an all-{@code tableHeader} first row is rendered. GFM
 * tables require a header row, so the default keeps such tables (and any genuinely inexpressible
 * table — number column, colspan/rowspan, non-GFM cell content) as raw HTML.
 */
public enum TableFallback {
  /** Emit raw {@code <table>} HTML for any table without an all-header first row (default). */
  HTML,
  /** Treat the first row as the header row so the table renders as GFM Markdown. */
  GFM_PROMOTE_FIRST_ROW,
  /** Prepend a synthesized empty header row so every original row stays a data row in GFM Markdown. */
  GFM_EMPTY_HEADER
}
