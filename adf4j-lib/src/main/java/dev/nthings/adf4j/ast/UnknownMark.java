package dev.nthings.adf4j.ast;

/// Fallback for a mark `type` the parser does not recognize. Keeps the original `type` and full
/// `rawJson`. By default the renderer leaves the marked text unformatted. Consumers matching on
/// {@link AdfMark} should handle this case.
public record UnknownMark(String type, String rawJson) implements AdfMark, AdfUnknown {

  /// Always empty; attributes are inside `rawJson`.
  @Override
  public Attributes attrs() {
    return Attributes.empty();
  }
}
