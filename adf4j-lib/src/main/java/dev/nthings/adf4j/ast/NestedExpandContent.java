package dev.nthings.adf4j.ast;

/// The node types the ADF schema allows directly inside `nestedExpand`. Records hold any child, so
/// that every parsed document fits; this names the types for code that constructs documents. It
/// covers node types only, not the marks, position, count or attributes a child also needs.
/// `AdfContentModelTests` checks it against the schema.
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
