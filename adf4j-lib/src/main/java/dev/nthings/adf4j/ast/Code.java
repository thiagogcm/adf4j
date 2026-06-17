package dev.nthings.adf4j.ast;

/// Inline-code (`code`) text mark; renders as a backtick span and supersedes any other formatting
/// marks on the same text (the content is taken literally).
public record Code() implements AdfMark {

  @Override
  public String type() {
    return "code";
  }
}
