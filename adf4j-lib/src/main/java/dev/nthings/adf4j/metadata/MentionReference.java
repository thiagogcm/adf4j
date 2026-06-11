package dev.nthings.adf4j.metadata;

/**
 * A user mention: the first non-blank of the mention's account id / local id, and its display
 * {@code text} (usually {@code @Name}), either of which may be {@code null} when the source omits it.
 */
public record MentionReference(String id, String text) {}
