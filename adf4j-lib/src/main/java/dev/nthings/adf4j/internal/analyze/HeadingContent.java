package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Anchor;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/// Interpretation of a heading's inline content, shared by the analyze phase (slugging) and the
/// render phase (emission), so both agree on which nodes a heading "is" and what explicit anchor
/// it carries.
public final class HeadingContent {

  private HeadingContent() {}

  /// The heading's significant inline nodes: leading/trailing {@link HardBreak}s trimmed and any
  /// Confluence `anchor` macro removed (its id is read separately via {@link #extractAnchorId}).
  public static List<AdfInline> normalizedHeadingNodes(List<AdfInline> content) {
    return normalized(content, Function.identity());
  }

  /// {@link #normalizedHeadingNodes} over items that each carry an inline, such as located ones.
  public static <T> List<T> normalized(List<T> content, Function<T, AdfInline> inline) {
    var start = 0;
    while (start < content.size() && inline.apply(content.get(start)) instanceof HardBreak) {
      start++;
    }
    var end = content.size();
    while (end > start && inline.apply(content.get(end - 1)) instanceof HardBreak) {
      end--;
    }
    if (start >= end) {
      return List.of();
    }

    return content.subList(start, end).stream()
        .filter(item -> !isAnchorExtension(inline.apply(item)))
        .toList();
  }

  /// The id of the heading's first Confluence `anchor` macro that has one, or `null` if none.
  static @Nullable String extractAnchorId(List<AdfInline> content) {
    for (var node : content) {
      if (node instanceof InlineExtension extension
          && ConfluenceMacro.of(extension) instanceof Anchor(var id)
          && id != null) {
        return id;
      }
    }
    return null;
  }

  private static boolean isAnchorExtension(AdfInline node) {
    return node instanceof InlineExtension extension
        && ConfluenceMacro.of(extension) instanceof Anchor;
  }
}
