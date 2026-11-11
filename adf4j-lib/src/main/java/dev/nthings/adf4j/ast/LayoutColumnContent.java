package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `layoutColumn`. {@link Adf} accepts only these;
/// parsed documents may hold any child. Node types only, not marks, position, count or attributes.
public sealed interface LayoutColumnContent extends AdfBlock
    permits BlockCard,
        Blockquote,
        BodiedExtension,
        BulletList,
        CodeBlock,
        DecisionList,
        EmbedCard,
        Expand,
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
