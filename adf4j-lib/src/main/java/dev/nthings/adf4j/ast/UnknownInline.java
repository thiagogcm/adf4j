package dev.nthings.adf4j.ast;

/// Fallback for an inline `type` the parser does not recognize. Keeps the original `type` and full
/// `rawJson`. Emission is governed by an `UnknownNodePolicy`. Consumers matching on {@link
/// AdfInline} should handle this case.
public record UnknownInline(String type, String rawJson) implements AdfInline, AdfUnknown {

  /// Always empty; attributes are inside `rawJson`.
  @Override
  public Attributes attrs() {
    return Attributes.empty();
  }
}
