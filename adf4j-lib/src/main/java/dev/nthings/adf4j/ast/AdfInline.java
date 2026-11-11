package dev.nthings.adf4j.ast;

/// An inline ADF node: text and the atoms that flow with it. An unrecognized `type` parses as
/// {@link UnknownInline}. The permits list grows with ADF (see {@link AdfNode}).
public sealed interface AdfInline extends AdfNode
    permits Text,
        HardBreak,
        InlineCard,
        BlockCard,
        EmbedCard,
        MediaInline,
        Date,
        Emoji,
        Mention,
        Placeholder,
        Status,
        InlineExtension,
        UnknownInline,
        CaptionContent,
        InlineContent {}
