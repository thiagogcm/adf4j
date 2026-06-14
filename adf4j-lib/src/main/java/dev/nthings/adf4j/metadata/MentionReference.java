package dev.nthings.adf4j.metadata;

import org.jspecify.annotations.Nullable;

/**
 * A user mention: the first non-blank of the mention's account id / local id, and its display
 * {@code text} (usually {@code @Name}), either of which may be {@code null} when the source omits it.
 */
public record MentionReference(@Nullable String id, @Nullable String text) {}
