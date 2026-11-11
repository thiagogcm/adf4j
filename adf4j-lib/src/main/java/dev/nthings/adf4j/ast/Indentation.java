package dev.nthings.adf4j.ast;

/// Indentation mark. `level` (default `0`) is clamped to 0..6 on render; each level is four leading
/// spaces.
public record Indentation(Attributes attrs) implements ParagraphMark, HeadingMark {

  @Override
  public String type() {
    return "indentation";
  }

  public int level() {
    return attrs.integer("level", 0);
  }
}
