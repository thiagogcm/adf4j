package dev.nthings.adf4j.ast;

/// The fallback for a block-level ADF `type` the parser does not recognize: it preserves the
/// node's original ADF `type` string and its full `rawJson` so an unsupported or newer node
/// survives parsing without data loss. How the renderer emits it is governed by an
/// `UnknownNodePolicy`. Consumers matching on {@link AdfBlock} should keep a `default`/handle
/// this case. See {@link UnknownInline} and {@link UnknownMark} for the inline/mark equivalents.
public record UnknownBlock(String type, String rawJson) implements AdfBlock {}
