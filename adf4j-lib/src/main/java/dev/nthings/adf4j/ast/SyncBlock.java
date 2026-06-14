package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record SyncBlock(@Nullable String resourceId) implements AdfBlock {}
