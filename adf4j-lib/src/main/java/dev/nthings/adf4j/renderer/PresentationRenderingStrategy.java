package dev.nthings.adf4j.renderer;

import dev.nthings.adf4j.model.BlockStyles;

final class PresentationRenderingStrategy implements RenderingStrategy {

  @Override
  public boolean isStorage() {
    return false;
  }

  @Override
  public String formatParagraph(String rendered, BlockStyles blockStyles) {
    if (!blockStyles.hasStyles() || rendered.isBlank()) {
      return rendered;
    }

    return HtmlFragments.paragraph(rendered, blockStyles);
  }

  @Override
  public String formatHeading(int level, String text, String anchor, BlockStyles blockStyles) {
    return HtmlFragments.heading(level, text, anchor, blockStyles);
  }

  @Override
  public boolean omitsVisualOnlyMarks() {
    return false;
  }

  @Override
  public boolean usesStyledInlineMedia() {
    return true;
  }
}
