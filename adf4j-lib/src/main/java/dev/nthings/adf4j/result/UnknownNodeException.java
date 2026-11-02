package dev.nthings.adf4j.result;

import dev.nthings.adf4j.options.UnknownNodePolicy;

/// Aborts a conversion that meets an unknown node under {@link UnknownNodePolicy#FAIL}.
public final class UnknownNodeException extends RuntimeException {

  private final String nodeType;

  public UnknownNodeException(String nodeType, String message) {
    super(message);
    this.nodeType = nodeType;
  }

  /// The ADF `type` of the node that aborted the conversion, as the document wrote it.
  public String nodeType() {
    return nodeType;
  }
}
