package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record Status(
    @Nullable String text, @Nullable String color, @Nullable String style, @Nullable String localId)
    implements AdfInline {}
