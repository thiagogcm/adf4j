package dev.nthings.adf4j.ast;

import java.util.List;

/// An inline smart link chip within text; its data is read through {@link AdfCard}. Block forms:
/// {@link BlockCard}, {@link EmbedCard}.
public record InlineCard(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, AdfCard, CaptionContent, InlineContent {

  public InlineCard {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "inlineCard";
  }
}
