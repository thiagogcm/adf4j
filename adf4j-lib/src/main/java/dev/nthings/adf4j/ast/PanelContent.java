package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `panel`. {@link Adf} accepts only these; parsed
/// documents may hold any child. Node types only, not marks, position, count or attributes.
public sealed interface PanelContent extends AdfBlock
    permits BlockCard,
        BulletList,
        CodeBlock,
        DecisionList,
        Extension,
        Heading,
        MediaGroup,
        MediaSingle,
        OrderedList,
        Paragraph,
        Rule,
        TaskList {}
