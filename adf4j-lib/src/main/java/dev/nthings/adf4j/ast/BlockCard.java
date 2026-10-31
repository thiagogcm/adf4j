package dev.nthings.adf4j.ast;

import java.util.List;

/// A block-level smart link: a URL or datasource rendered as its own block (a rich link card).
/// All of its data lives on {@link CardAttrs}: a `url`-backed card links out, a
/// `datasourceId`-backed card references a datasource by id. See {@link InlineCard} for the in-text
/// form and {@link EmbedCard} for the iframe-style embed.
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
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "blockCard";
  }
}
