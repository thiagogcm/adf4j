package dev.nthings.adf4j.ast;

/**
 * A block-level ADF node. A {@code type} the parser does not recognize parses as {@link UnknownBlock}
 * with its raw JSON preserved. The permits list grows with ADF — see the {@link AdfNode} note.
 */
public sealed interface AdfBlock extends AdfNode
    permits Paragraph,
        Heading,
        Blockquote,
        CodeBlock,
        Panel,
        Rule,
        BulletList,
        OrderedList,
        ListItem,
        TaskList,
        TaskItem,
        BlockTaskItem,
        DecisionList,
        DecisionItem,
        Table,
        TableRow,
        TableCell,
        MediaSingle,
        MediaGroup,
        Media,
        Caption,
        Expand,
        NestedExpand,
        LayoutSection,
        LayoutColumn,
        Extension,
        BodiedExtension,
        MultiBodiedExtension,
        ExtensionFrame,
        SyncBlock,
        BodiedSyncBlock,
        BlockCard,
        EmbedCard,
        UnknownBlock {}
