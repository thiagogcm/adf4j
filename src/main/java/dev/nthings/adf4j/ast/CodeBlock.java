package dev.nthings.adf4j.ast;

public record CodeBlock(String language, String text) implements AdfBlock {

  public CodeBlock {
    text = text == null ? "" : text;
  }
}
