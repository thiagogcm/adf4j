package dev.nthings.adf4j.ast;

/// The fallback for a mark `type` the parser does not recognize: it preserves the mark's
/// original ADF `type` string and full `rawJson` so an unsupported or newer mark survives without
/// data loss. By default the renderer leaves the marked text alone (it applies no formatting).
/// Consumers matching on {@link AdfMark} should keep a `default`/handle this case. See
/// {@link UnknownBlock}/{@link UnknownInline} for the node-level equivalents.
public record UnknownMark(String type, String rawJson) implements AdfMark {}
