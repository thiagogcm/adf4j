package dev.nthings.adf4j.result;

/// An intentional or fallback change to the source, independent of diagnostic severity.
public record ConversionEffect(Kind kind, String path, String message) {
  public enum Kind {
    CONTENT_DROPPED,
    PLACEHOLDER,
    STYLE_OMITTED,
    STRUCTURE_CHANGED
  }
}
