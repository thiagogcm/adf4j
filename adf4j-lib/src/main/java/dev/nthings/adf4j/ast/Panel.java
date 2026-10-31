package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `panel`: a callout box wrapping `content` blocks. `panelType` selects the variant
/// (e.g. `info`/`note`/`warning`/`success`/`error`), or `null` when unspecified.
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
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "panel";
  }

  public @Nullable String panelType() {
    return attrs.text("panelType");
  }
}
