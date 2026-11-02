package dev.nthings.adf4j.internal.render;

/// The surroundings inline nodes render into. A {@link #GFM_CELL} pipe cell is inline context, so
/// leading block markers are harmless and neutralization is suppressed; an {@link #HTML_CELL}
/// fragment is re-parsed as a block document, so a leading `#`/`>`/`-` must be neutralized. Both
/// collapse a hard break to a single `"\n"`. A {@link #HEADING} is one ATX line that follows the
/// `# ` marker: nothing in it starts a line, and a hard break collapses to a space. A heading in a
/// cell is a {@link #HEADING} only: its text is never at a line start, so the cell's escaping rule
/// never applies, and it holds no blocks that could return to the cell.
enum InlineSite {
  BODY,
  GFM_CELL,
  HTML_CELL,
  HEADING
}
