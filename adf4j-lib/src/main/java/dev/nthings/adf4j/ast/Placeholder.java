package dev.nthings.adf4j.ast;

/// Editor placeholder prompt text (the ghost text shown in an empty field); `text` renders
/// verbatim as inline text.
public record Placeholder(String text) implements AdfInline {}
