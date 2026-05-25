package dev.nthings.adf4j.ast;

public record Status(String text, String color, String style, String localId)
    implements AdfInline {}
