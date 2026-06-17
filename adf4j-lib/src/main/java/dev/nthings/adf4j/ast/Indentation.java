package dev.nthings.adf4j.ast;

/// Indentation (`indentation`) mark. `level` is the indent depth (default `0`, clamped to 0..6
/// when rendered), emitted as a leading run of four spaces per level on the paragraph or heading.
public record Indentation(int level) implements AdfMark {

  @Override
  public String type() {
    return "indentation";
  }
}
