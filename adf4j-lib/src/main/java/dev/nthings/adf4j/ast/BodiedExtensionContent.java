package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `bodiedExtension`. {@link Adf} accepts only
/// these; parsed documents may hold any child. Node types only, not marks, position, count or
/// attributes.
public sealed interface BodiedExtensionContent extends AdfBlock
    permits BlockCard,
        Blockquote,
        BulletList,
        CodeBlock,
        DecisionList,
        EmbedCard,
        Extension,
        Heading,
        MediaGroup,
        MediaSingle,
        OrderedList,
        Panel,
        Paragraph,
        Rule,
        Table,
        TaskList {}
