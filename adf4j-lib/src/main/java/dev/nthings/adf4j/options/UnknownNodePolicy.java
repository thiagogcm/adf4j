package dev.nthings.adf4j.options;

/** How the renderer treats ADF node types it does not recognise. */
public enum UnknownNodePolicy {
  /** Emit a visible {@code [Unsupported: <type>]} marker in place of the node. */
  PLACEHOLDER,
  /** Drop the node from the output entirely. */
  SKIP,
  /** Abort the conversion with an {@link IllegalStateException}. */
  FAIL,
  /**
   * Preserve an unknown node's original JSON (a fenced {@code json} block for a block node, an inline
   * code span for an inline node) for a round-trippable archive. Covers node <em>types</em> only;
   * unknown <em>marks</em> have no standalone form and are still dropped, but reported via
   * {@link dev.nthings.adf4j.result.MarkdownResult#wasLossy()}.
   */
  PRESERVE_RAW
}
