package dev.nthings.adf4j.metadata;

import org.jspecify.annotations.Nullable;

/// A user mention. `id` is the first non-blank of the account ID or local ID; `text` is usually
/// `@Name`. Either may be null.
public record MentionReference(@Nullable String id, @Nullable String text) {}
