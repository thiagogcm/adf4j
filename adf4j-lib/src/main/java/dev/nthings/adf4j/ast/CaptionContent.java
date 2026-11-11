package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `caption`. {@link Adf} accepts only these;
/// parsed documents may hold any child. Node types only, not marks, position, count or attributes.
public sealed interface CaptionContent extends AdfInline
    permits Date, Emoji, HardBreak, InlineCard, Mention, Placeholder, Status, Text {}
