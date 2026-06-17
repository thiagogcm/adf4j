package dev.nthings.adf4j.ast;

/// The inline counterpart of {@link UnknownBlock}: the fallback for an inline ADF `type` the
/// parser does not recognize, preserving the original `type` string and full `rawJson` so the
/// node survives without data loss. Emission is governed by an `UnknownNodePolicy`; consumers
/// matching on {@link AdfInline} should keep a `default`/handle this case.
public record UnknownInline(String type, String rawJson) implements AdfInline {}
