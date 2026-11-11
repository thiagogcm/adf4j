package dev.nthings.adf4j.internal.render;

/// Where inline nodes render. GFM_CELL is inline context: no block-marker neutralization. HTML_CELL
/// is re-parsed as blocks, so a leading `#`/`>`/`-` is neutralized. Both cells collapse a hard
/// break to `"\n"`. HEADING is one ATX line after `# `: nothing starts a line, and a hard break
/// becomes a space. A heading in a cell is HEADING, never at a line start and never holding blocks.
enum InlineSite {
  BODY,
  GFM_CELL,
  HTML_CELL,
  HEADING
}
