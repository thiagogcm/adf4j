package dev.nthings.adf4j.ast;

/// Breakout (`breakout`) mark that widens a node (e.g. a code block or layout) beyond the content
/// column. Layout-only, with no Markdown equivalent, so it is dropped from the output.
public record Breakout() implements AdfMark {

  @Override
  public String type() {
    return "breakout";
  }
}
