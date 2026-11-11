package dev.nthings.adf4j.ast;

import java.util.List;

/// A block-level embedded smart link: an iframe-style preview of a URL, read through {@link
/// AdfCard}. Renders to a Markdown link when it has a `url` or title, else a placeholder label,
/// since Markdown has no live embed. {@link BlockCard} and {@link InlineCard} are the other forms.
public record EmbedCard(Attributes attrs, List<AdfMark> marks)
    implements AdfBlock,
        AdfInline,
        AdfCard,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        TableCellContent {

  public EmbedCard {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "embedCard";
  }
}
