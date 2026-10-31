package dev.nthings.adf4j.ast;

import java.util.List;

/// An inline smart link: a URL rendered within the flow of text as a rich link chip. Its data
/// lives on {@link CardAttrs}, normally a `url`. See {@link BlockCard} and {@link EmbedCard} for
/// the block-level forms.
public record InlineCard(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, AdfCard, CaptionContent, InlineContent {

  public InlineCard {
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "inlineCard";
  }
}
