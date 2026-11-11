package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A callout box wrapping `content`. `panelType` (e.g. `info`, `warning`) is `null` when
/// unspecified.
public record Panel(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        NestedExpandContent,
        TableCellContent {

  public Panel {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "panel";
  }

  public @Nullable String panelType() {
    return attrs.text("panelType");
  }
}
