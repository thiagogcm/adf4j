package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record Mention(
    @Nullable String id,
    String text,
    @Nullable String userType,
    @Nullable String accessLevel,
    @Nullable String localId)
    implements AdfInline {}
