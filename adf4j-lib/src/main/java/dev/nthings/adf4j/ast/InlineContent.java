package dev.nthings.adf4j.ast;

/// The node types the ADF schema allows directly inside `decisionItem`, `heading`, `paragraph`,
/// `taskItem`. Records hold any child, so that every parsed document fits; {@link Adf} takes this
// type to refuse others at compile time. It covers node types only, not the marks, position, count
// or
/// attributes a child also needs. `AdfContentModelTests` checks it against the schema.
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
