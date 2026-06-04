package dev.nthings.adf4j.internal.analyze;

import java.util.List;

import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.internal.ConfluenceSupport;

/**
 * Interpretation of a heading's inline content, shared by the analyze phase (slugging) and the render
 * phase (emission), so both agree on which nodes a heading "is" and what explicit anchor it carries.
 */
public final class HeadingContent {

  private HeadingContent() {
  }

  /**
   * The heading's significant inline nodes: leading and trailing {@link HardBreak}s trimmed and any
   * Confluence {@code anchor} macro removed (its id is read separately via {@link #extractAnchorId}).
   */
  public static List<AdfInline> normalizedHeadingNodes(List<AdfInline> content) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }

    var start = 0;
    while (start < content.size() && content.get(start) instanceof HardBreak) {
      start++;
    }
    var end = content.size();
    while (end > start && content.get(end - 1) instanceof HardBreak) {
      end--;
    }
    if (start >= end) {
      return List.of();
    }

    return content.subList(start, end).stream()
        .filter(node -> !isAnchorExtension(node))
        .toList();
  }

  /** True iff the heading carries an explicit Confluence {@code anchor} macro with a non-blank id. */
  public static boolean hasExplicitAnchor(List<AdfInline> content) {
    var anchorId = extractAnchorId(content);
    return anchorId != null && !anchorId.isBlank();
  }

  /** The id of the heading's explicit Confluence {@code anchor} macro, or {@code null} if none. */
  public static String extractAnchorId(List<AdfInline> content) {
    if (content == null || content.isEmpty()) {
      return null;
    }
    for (var node : content) {
      if (!(node instanceof InlineExtension extension)) {
        continue;
      }
      if (!isAnchorExtension(extension)) {
        continue;
      }
      var anchorId = ConfluenceSupport.anchorId(extension.macroParams());
      if (anchorId != null && !anchorId.isBlank()) {
        return anchorId;
      }
    }
    return null;
  }

  private static boolean isAnchorExtension(AdfInline node) {
    if (!(node instanceof InlineExtension extension)) {
      return false;
    }
    return ConfluenceSupport.isConfluenceMacroExtension(extension.extensionType())
        && "anchor".equals(extension.extensionKey());
  }
}
