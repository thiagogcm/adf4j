package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Anchor;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/// Heading inline content, shared by analyze (slugging) and render (emission) so both agree on
/// what a heading "is" and its explicit anchor.
public final class HeadingContent {

  private HeadingContent() {}

  /// Inline nodes without leading or trailing {@link HardBreak}s and without `anchor` macros,
  /// whose ids come from {@link #extractAnchorId}.
  public static List<AdfInline> normalizedHeadingNodes(List<AdfInline> content) {
    return normalized(content, Function.identity());
  }

  /// {@link #normalizedHeadingNodes} for items that wrap an inline, such as located ones.
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

  /// Id of the first `anchor` macro that carries one, or `null`.
  public static @Nullable String extractAnchorId(List<AdfInline> content) {
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
