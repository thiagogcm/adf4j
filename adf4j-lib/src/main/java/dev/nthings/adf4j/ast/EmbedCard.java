package dev.nthings.adf4j.ast;

import java.util.List;

/// A block-level embedded smart link: the iframe-style preview of a URL (video, document, …).
/// Its data is read through {@link AdfCard}, normally a `url`. Renders to a Markdown link when it
// has a
/// `url` or title, otherwise a placeholder label, since Markdown has no live embed. See
/// {@link BlockCard} for the plain card and {@link InlineCard} for the in-text form.
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
