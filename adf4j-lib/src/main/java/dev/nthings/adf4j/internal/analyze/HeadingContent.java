package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Interpretation of a heading's inline content, shared by the analyze phase (slugging) and the
/// render phase (emission), so both agree on which nodes a heading "is" and what explicit anchor
/// it carries.
public final class HeadingContent {

  private HeadingContent() {}

  /// The heading's significant inline nodes: leading/trailing {@link HardBreak}s trimmed and any
  /// Confluence `anchor` macro removed (its id is read separately via {@link #extractAnchorId}).
  public static List<AdfInline> normalizedHeadingNodes(@Nullable List<AdfInline> content) {
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

    return content.subList(start, end).stream().filter(node -> !isAnchorExtension(node)).toList();
  }

  /// True iff the heading carries an explicit Confluence `anchor` macro with a non-blank id.
  public static boolean hasExplicitAnchor(@Nullable List<AdfInline> content) {
    var anchorId = extractAnchorId(content);
    return anchorId != null && !anchorId.isBlank();
  }

  /// The id of the heading's explicit Confluence `anchor` macro, or `null` if none.
  public static @Nullable String extractAnchorId(@Nullable List<AdfInline> content) {
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
