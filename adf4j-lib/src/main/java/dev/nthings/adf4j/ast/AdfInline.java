package dev.nthings.adf4j.ast;

/// An inline ADF node (text and the atoms that flow with it). A `type` the parser does not
/// recognize parses as {@link UnknownInline}. The permits list grows with ADF (see the
/// {@link AdfNode} note).
public sealed interface AdfInline extends AdfNode
    permits Text,
        HardBreak,
        InlineCard,
        MediaInline,
        Date,
        Emoji,
        Mention,
        Placeholder,
        Status,
        InlineExtension,
        UnknownInline {}
