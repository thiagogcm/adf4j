package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// An inline emoji. `text` is the rendered glyph (e.g. the Unicode character) and `shortName` its
/// colon code (e.g. `:smile:`); rendering prefers `text`, falling back to `shortName`. `id` is the
/// emoji provider's identifier.
public record Emoji(@Nullable String id, @Nullable String text, @Nullable String shortName)
    implements AdfInline {}
