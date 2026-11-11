package dev.nthings.adf4j.ast;

/// Fallback for a block `type` the parser does not recognize. Keeps the original `type` and full
/// `rawJson`, so newer nodes survive parsing. Emission is governed by an `UnknownNodePolicy`.
/// Consumers matching on {@link AdfBlock} should handle this case.
public record UnknownBlock(String type, String rawJson) implements AdfBlock, AdfUnknown {

  /// Always empty; attributes are inside `rawJson`.
  @Override
  public Attributes attrs() {
    return Attributes.empty();
  }
}
