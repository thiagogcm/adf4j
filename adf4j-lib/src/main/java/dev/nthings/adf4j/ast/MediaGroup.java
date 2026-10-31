package dev.nthings.adf4j.ast;

import java.util.List;

/// A cluster of attachments shown together: the outer wrapper around one or more {@link Media}
/// children, typically the non-image files (PDFs, archives, …) of a page. Unlike
/// {@link MediaSingle} it carries no layout or width and takes no `marks`; per-item attributes
/// live on each child's {@link MediaAttrs}.
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
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "mediaGroup";
  }
}
