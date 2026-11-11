package dev.nthings.adf4j.ast;

import java.util.List;

/// A block-level smart link, rendered as its own block. Its data is read through {@link AdfCard}: a
/// `url` card links out, a `datasourceId` card references a datasource. See {@link InlineCard} and
/// {@link EmbedCard} for the other forms.
public record BlockCard(Attributes attrs, List<AdfMark> marks)
    implements AdfBlock,
        AdfInline,
        AdfCard,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        PanelContent,
        TableCellContent {

  public BlockCard {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "blockCard";
  }
}
