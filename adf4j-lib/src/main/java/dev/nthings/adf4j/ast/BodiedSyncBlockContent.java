package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `bodiedSyncBlock`. {@link Adf} accepts only
/// these; parsed documents may hold any child. Node types only, not marks, position, count or
/// attributes.
public sealed interface BodiedSyncBlockContent extends AdfBlock
    permits BlockCard,
        Blockquote,
        BulletList,
        CodeBlock,
        DecisionList,
        EmbedCard,
        Expand,
        Heading,
        LayoutSection,
        MediaGroup,
        MediaSingle,
        OrderedList,
        Panel,
        Paragraph,
        Rule,
        Table,
        TaskList {}
