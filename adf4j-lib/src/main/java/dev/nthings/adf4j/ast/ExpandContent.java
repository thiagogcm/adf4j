package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `expand`. {@link Adf} accepts only these;
/// parsed documents may hold any child. Node types only, not marks, position, count or attributes.
public sealed interface ExpandContent extends AdfBlock
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
        NestedExpand,
        OrderedList,
        Panel,
        Paragraph,
        Rule,
        Table,
        TaskList {}
