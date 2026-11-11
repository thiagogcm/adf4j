package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `nestedExpand`. {@link Adf} accepts only these;
/// parsed documents may hold any child. Node types only, not marks, position, count or attributes.
public sealed interface NestedExpandContent extends AdfBlock
    permits Blockquote,
        BulletList,
        CodeBlock,
        DecisionList,
        Extension,
        Heading,
        MediaGroup,
        MediaSingle,
        OrderedList,
        Panel,
        Paragraph,
        Rule,
        TaskList {}
