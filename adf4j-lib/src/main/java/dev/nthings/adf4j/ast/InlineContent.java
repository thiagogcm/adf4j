package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `decisionItem`, `heading`, `paragraph` and
/// `taskItem`. {@link Adf} accepts only these; parsed documents may hold any child. Node types
/// only, not marks, position, count or attributes.
public sealed interface InlineContent extends AdfInline
    permits Date,
        Emoji,
        HardBreak,
        InlineCard,
        InlineExtension,
        MediaInline,
        Mention,
        Placeholder,
        Status,
        Text {}
