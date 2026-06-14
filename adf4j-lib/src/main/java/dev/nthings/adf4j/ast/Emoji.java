package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record Emoji(@Nullable String id, @Nullable String text, @Nullable String shortName)
    implements AdfInline {}
