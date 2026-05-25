package dev.nthings.adf4j.renderer;

import dev.nthings.adf4j.model.BlockStyles;

final class StorageRenderingStrategy implements RenderingStrategy {

  @Override
  public boolean isStorage() {
    return true;
  }

  @Override
  public String formatParagraph(String rendered, BlockStyles blockStyles) {
    return rendered;
  }

  @Override
  public String formatHeading(int level, String text, String anchor, BlockStyles blockStyles) {
    var heading = "%s %s".formatted("#".repeat(level), text).trim();
    if (anchor != null && !anchor.isBlank()) {
      return HtmlFragments.anchor(anchor) + "\n" + heading;
    }
    return heading;
  }

  @Override
  public boolean omitsVisualOnlyMarks() {
    return true;
  }

  @Override
  public boolean usesStyledInlineMedia() {
    return false;
  }
}
