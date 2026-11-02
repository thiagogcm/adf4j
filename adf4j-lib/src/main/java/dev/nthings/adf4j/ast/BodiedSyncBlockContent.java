package dev.nthings.adf4j.ast;

/// The node types the ADF schema allows directly inside `bodiedSyncBlock`. Records hold any child,
/// so that every parsed document fits; {@link Adf} takes this type to refuse others at compile
/// time. It covers node types only, not the marks, position, count or attributes a child also
/// needs. `AdfContentModelTests` checks it against the schema.
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
