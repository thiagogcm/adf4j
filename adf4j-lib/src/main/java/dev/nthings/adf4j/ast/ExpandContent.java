package dev.nthings.adf4j.ast;

/// The node types the ADF schema allows directly inside `expand`. Records hold any child, so that
/// every parsed document fits; this names the types for code that constructs documents. It covers
/// node types only, not the marks, position, count or attributes a child also needs.
/// `AdfContentModelTests` checks it against the schema.
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
