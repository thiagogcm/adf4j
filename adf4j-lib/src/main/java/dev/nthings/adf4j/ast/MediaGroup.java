package dev.nthings.adf4j.ast;

import java.util.List;

/// A cluster of attachments shown together: the outer wrapper around one or more {@link Media}
/// children, typically the non-image files (PDFs, archives, …) of a page. Unlike
/// {@link MediaSingle} it carries no layout or width and takes no `marks`; per-item attributes
/// live on each child's {@link AdfMedia} fields.
public record MediaGroup(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BlockquoteContent,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent {

  public MediaGroup {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "mediaGroup";
  }

  /// The {@link Media} children; any off-schema child stays in `content` only.
  public List<Media> items() {
    return ChildNodes.ofType(content, Media.class);
  }
}
