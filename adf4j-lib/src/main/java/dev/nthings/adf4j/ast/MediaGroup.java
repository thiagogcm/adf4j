package dev.nthings.adf4j.ast;

import java.util.List;

/// A cluster of attachments shown together, typically non-image files. Unlike {@link MediaSingle}
/// it has no layout, width or `marks`; per-item attributes live on each child.
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

  /// The {@link Media} children; other children stay only in `content`.
  public List<Media> items() {
    return ChildNodes.ofType(content, Media.class);
  }
}
