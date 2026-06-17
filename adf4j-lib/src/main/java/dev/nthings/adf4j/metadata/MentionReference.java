package dev.nthings.adf4j.metadata;

import org.jspecify.annotations.Nullable;

/// A user mention: the first non-blank of the mention's account id / local id, and its display
/// `text` (usually `@Name`), either of which may be `null` when the source omits it.
public record MentionReference(@Nullable String id, @Nullable String text) {}
