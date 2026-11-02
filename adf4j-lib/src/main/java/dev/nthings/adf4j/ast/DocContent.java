package dev.nthings.adf4j.ast;

/// The node types the ADF schema allows directly inside `doc`. Records hold any child, so that
/// every parsed document fits; {@link Adf} takes this type to refuse others at compile time. It
// covers
/// node types only, not the marks, position, count or attributes a child also needs.
/// `AdfContentModelTests` checks it against the schema.
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
