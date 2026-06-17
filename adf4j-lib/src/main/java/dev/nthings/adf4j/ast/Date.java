package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// An inline date pill. `timestamp` is a Unix epoch value as a string (interpreted as seconds when
/// small, otherwise milliseconds) and renders as a UTC ISO date (`yyyy-MM-dd`). A `null`, blank,
/// or unparseable `timestamp` yields no date (the raw value is kept if non-numeric).
/// `localId` is an editor-local identity, not part of the rendered output.
public record Date(@Nullable String timestamp, @Nullable String localId) implements AdfInline {}
