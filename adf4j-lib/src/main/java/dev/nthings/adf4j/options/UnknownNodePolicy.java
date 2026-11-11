package dev.nthings.adf4j.options;

/// Rendering of unknown ADF nodes. Parsing always preserves their raw JSON.
public enum UnknownNodePolicy {
  PLACEHOLDER,
  SKIP,
  PRESERVE_RAW,
  /// Fails the conversion with UNKNOWN_NODE_REJECTED, citing the source location.
  FAIL
}
