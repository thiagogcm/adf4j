package dev.nthings.adf4j.ast;

/// The node types the ADF schema allows directly inside `layoutColumn`. Records hold any child, so
/// that every parsed document fits; this names the types for code that constructs documents. It
/// covers node types only, not the marks, position, count or attributes a child also needs.
/// `AdfContentModelTests` checks it against the schema.
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
