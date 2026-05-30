package dev.nthings.adf4j.ast;

public record UnknownInline(String type, String rawJson) implements AdfInline {}
