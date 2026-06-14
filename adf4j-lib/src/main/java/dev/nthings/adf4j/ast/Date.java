package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record Date(@Nullable String timestamp, @Nullable String localId) implements AdfInline {}
