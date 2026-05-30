package dev.nthings.adf4j.renderer;

import dev.nthings.adf4j.model.BlockStyles;

public interface RenderingStrategy {

  boolean isStorage();

  String formatParagraph(String rendered, BlockStyles blockStyles);

  String formatHeading(int level, String text, String anchor, BlockStyles blockStyles);

  boolean omitsVisualOnlyMarks();

  boolean usesStyledInlineMedia();
}
