package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `doc`. {@link Adf} accepts only these; parsed
/// documents may hold any child. Node types only, not marks, position, count or attributes.
public sealed interface DocContent extends AdfBlock
    permits BlockCard,
        Blockquote,
        BodiedExtension,
        BodiedSyncBlock,
        BulletList,
        CodeBlock,
        DecisionList,
        EmbedCard,
        Expand,
        Extension,
        Heading,
        LayoutSection,
        MediaGroup,
        MediaSingle,
        OrderedList,
        Panel,
        Paragraph,
        Rule,
        SyncBlock,
        Table,
        TaskList {}
