package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `codeBlock`. `language` is the source language hint (e.g. `java`, `json`) used for the fence
/// info string, or `null` when none was declared. `text` is the verbatim code, normalized to `""`
/// when absent.
public record CodeBlock(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock,
        BlockquoteContent,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        ListItemContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent {

  public CodeBlock {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "codeBlock";
  }

  public @Nullable String language() {
    return attrs.text("language");
  }

  /// The verbatim code: the concatenated text of the {@link Text} children.
  public String text() {
    var builder = new StringBuilder();
    for (var child : content) {
      if (child instanceof Text text) {
        builder.append(text.text());
      }
    }
    return builder.toString();
  }
}
