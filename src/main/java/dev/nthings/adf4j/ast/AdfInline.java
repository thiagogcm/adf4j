package dev.nthings.adf4j.ast;

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
