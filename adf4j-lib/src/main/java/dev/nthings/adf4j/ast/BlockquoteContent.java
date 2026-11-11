package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `blockquote`. {@link Adf} accepts only these;
/// parsed documents may hold any child. Node types only, not marks, position, count or attributes.
public sealed interface BlockquoteContent extends AdfBlock
    permits BulletList, CodeBlock, Extension, MediaGroup, MediaSingle, OrderedList, Paragraph {}
