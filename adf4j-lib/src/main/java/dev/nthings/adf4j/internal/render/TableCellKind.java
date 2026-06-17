package dev.nthings.adf4j.internal.render;

/// Which kind of table cell the renderer is inside, if any. Splits the two concerns the old
/// `inTable` boolean conflated. A {@link #GFM} pipe cell is inline context (leading block markers
/// are harmless, so neutralization is suppressed); an {@link #HTML} fragment is re-parsed as a
/// block document (a leading `#`/`>`/`-` must be neutralized); {@link #NONE} is outside any cell.
/// Every cell collapses a hard break to a single `"\n"`.
enum TableCellKind {
  NONE,
  GFM,
  HTML
}
