package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.Alignment;
import dev.nthings.adf4j.ast.Annotation;
import dev.nthings.adf4j.ast.BackgroundColor;
import dev.nthings.adf4j.ast.Border;
import dev.nthings.adf4j.ast.Breakout;
import dev.nthings.adf4j.ast.Code;
import dev.nthings.adf4j.ast.DataConsumer;
import dev.nthings.adf4j.ast.Em;
import dev.nthings.adf4j.ast.FontSize;
import dev.nthings.adf4j.ast.Fragment;
import dev.nthings.adf4j.ast.Indentation;
import dev.nthings.adf4j.ast.Link;
import dev.nthings.adf4j.ast.Strike;
import dev.nthings.adf4j.ast.Strong;
import dev.nthings.adf4j.ast.SubSup;
import dev.nthings.adf4j.ast.TextColor;
import dev.nthings.adf4j.ast.Underline;
import dev.nthings.adf4j.ast.UnknownMark;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.Located;
import dev.nthings.adf4j.internal.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Applies marks to rendered text, and the block marks (indentation, alignment) to a whole block.
final class TextMarkRenderer {

  // U+00A0 per indent step; ASCII spaces are unreliable as leading indent (4+ become a code block).
  private static final String INDENT_UNIT = "    ";

  // Hex and rgb()/hsl() colors, keywords (`red`, `x-small`) and sizes (`20px`, `1.5em`, `120%`):
  // none can end a declaration or the attribute, or load anything.
  private static final Pattern SAFE_CSS_VALUE =
      Pattern.compile(
          "#\\p{XDigit}{3,8}"
              + "|\\p{Alpha}[\\p{Alpha}-]*"
              + "|\\d+(?:\\.\\d+)?(?:\\p{Alpha}+|%)?"
              + "|(?:rgb|hsl)a?\\([\\d.,%/ -]*\\)");

  private static final Pattern CSS_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

  /// What one mark does to rendered text, applied in `rank` order.
  private sealed interface MarkEffect {

    int rank();
  }

  /// Wraps the text in `open` and `close`.
  private record Wrap(int rank, String open, String close) implements MarkEffect {}

  /// One declaration of the opt-in visual `<span style>`.
  private record Css(int rank, String declaration) implements MarkEffect {}

  /// The link around a run. `pageNodeId` is what a `PageLinkResolver` will look up, so two links
  /// that could resolve differently differ here without asking the resolver.
  private record LinkTarget(String href, @Nullable String title, @Nullable String pageNodeId) {}

  /// What a mark list renders to under the active options, holding nothing that doesn't render:
  /// `effects` are the formatting by rank, a later mark replacing an earlier one of the same rank.
  /// A code mark makes the text literal, superseding them. Two mark lists render identically
  /// exactly when their styles are equal.
  private record Style(boolean code, Map<Integer, MarkEffect> effects, @Nullable LinkTarget link) {}

  // Inside-out: format marks (canonical order), then a combined visual <span>, then the link
  // outermost.
  // `context` sits at the node that carries `marks`.
  String applyMarks(String value, List<AdfMark> marks, RenderContext context) {
    if (marks.isEmpty()) {
      return value;
    }
    var style = style(marks, context.session());
    var rendered =
        style.code()
            ? MarkdownText.inlineCodeSpan(value)
            : applyEffects(value, style.effects().values());
    var link = style.link();
    return link == null ? rendered : applyLink(rendered, link, linkPath(marks, context), context);
  }

  /// Whether text runs with these marks render identically, and so must merge into one run. Asks no
  /// resolver: two different hrefs stay apart even when a `PageLinkResolver` maps them to one page.
  boolean rendersAlike(List<AdfMark> left, List<AdfMark> right, RenderSession session) {
    return left.equals(right) || style(left, session).equals(style(right, session));
  }

  /// Whether a visual-only mark leaves no trace in the output: always without `htmlVisualMarks`,
  /// and with it whenever its value is unsafe to emit. Alignment is a block mark; only the default
  /// alignment goes unwrapped, which loses nothing.
  static boolean omitsVisual(AdfMark mark, boolean htmlVisualMarks) {
    return switch (mark) {
      case TextColor _, BackgroundColor _, FontSize _, Border _ ->
          effect(mark, htmlVisualMarks) == null;
      case Alignment _ -> !htmlVisualMarks;
      default -> false;
    };
  }

  private static Style style(List<AdfMark> marks, RenderSession session) {
    var visual = session.options().htmlVisualMarks();
    var code = false;
    var effects = new TreeMap<Integer, MarkEffect>();
    for (var mark : marks) {
      code |= mark instanceof Code;
      var effect = effect(mark, visual);
      if (effect != null) {
        effects.put(effect.rank(), effect);
      }
    }
    return new Style(code, code ? Map.of() : effects, linkTarget(marks, session));
  }

  private static @Nullable MarkEffect effect(AdfMark mark, boolean visual) {
    return switch (mark) {
      case Strong _ -> new Wrap(0, "**", "**");
      case Em _ -> new Wrap(1, "*", "*");
      case Strike _ -> new Wrap(2, "~~", "~~");
      case SubSup s when "sup".equalsIgnoreCase(s.subSupType()) -> new Wrap(3, "<sup>", "</sup>");
      case SubSup s when "sub".equalsIgnoreCase(s.subSupType()) -> new Wrap(3, "<sub>", "</sub>");
      case Underline _ -> new Wrap(4, "<u>", "</u>");
      case FontSize size when visual -> css(5, "font-size", size.fontSize());
      case TextColor color when visual -> css(6, "color", color.color());
      case BackgroundColor color when visual -> css(7, "background-color", color.color());
      case Border border when visual -> border(8, border);
      // An unknown subsup type is left unwrapped rather than guessed; code and links are not
      // formatting (see style); the rest never touch the text.
      case SubSup _,
          FontSize _,
          TextColor _,
          BackgroundColor _,
          Border _,
          Code _,
          Link _,
          Alignment _,
          Indentation _,
          UnknownMark _,
          Annotation _,
          Breakout _,
          DataConsumer _,
          Fragment _ ->
          null;
    };
  }

  private static @Nullable LinkTarget linkTarget(List<AdfMark> marks, RenderSession session) {
    var link = AdfText.effectiveLink(marks);
    var href = link == null ? null : link.href();
    if (link == null || href == null || href.isBlank()) {
      return null;
    }
    return new LinkTarget(
        href,
        Strings.firstNonBlank(link.title()),
        session.resolvers().linkedPageId(href, link.attrs()));
  }

  // The pointer of the effective (last) link mark, which the PageLinkResolver call reports.
  private static String linkPath(List<AdfMark> marks, RenderContext context) {
    var index = marks.size() - 1;
    while (index > 0 && !(marks.get(index) instanceof Link)) index--;
    return Located.mark(context.path(), index);
  }

  private String applyEffects(String value, Collection<MarkEffect> effects) {
    var rendered = value;
    var declarations = new ArrayList<String>();
    for (var effect : effects) {
      switch (effect) {
        case Wrap wrap -> rendered = wrap(rendered, wrap.open(), wrap.close());
        case Css css -> declarations.add(css.declaration());
      }
    }
    // Opt-in: preserve visual-only marks as one combined <span style> instead of dropping them.
    if (!declarations.isEmpty()) {
      var style = String.join("; ", declarations);
      rendered = wrap(rendered, "<span style=\"" + style + "\">", "</span>");
    }
    return rendered;
  }

  // Wraps formatted text in a Markdown link. A PageLinkResolver rewrites an internal page href to
  // the caller's destination; the visible label (the original text, or the original href when the
  // text is blank) is left untouched.
  private String applyLink(
      String rendered, LinkTarget link, String linkPath, RenderContext context) {
    var session = context.session();
    var label = rendered.isBlank() ? session.writer().text(link.href(), false) : rendered;
    var resolved = session.resolvers().pageId(link.pageNodeId(), linkPath);
    var destination = resolved == null ? link.href() : resolved;
    var title = link.title();
    return title == null
        ? MarkdownText.linkRendered(label, destination)
        : MarkdownText.linkRendered(label, destination, title);
  }

  // The values are arbitrary JSON from untrusted ADF, so one that isn't a known-safe shape drops
  // the declaration.
  private static @Nullable MarkEffect css(int rank, String property, @Nullable String value) {
    var safe = safeCssValue(value);
    return safe == null ? null : new Css(rank, property + ":" + safe);
  }

  private static @Nullable MarkEffect border(int rank, Border border) {
    var size = Strings.trimToNull(border.size());
    var color = safeCssValue(border.color());
    if (size == null || color == null || !CSS_NUMBER.matcher(size).matches()) {
      return null;
    }
    return new Css(rank, "border:" + size + "px solid " + color);
  }

  private static @Nullable String safeCssValue(@Nullable String value) {
    var stripped = Strings.trimToNull(value);
    return stripped != null && SAFE_CSS_VALUE.matcher(stripped).matches() ? stripped : null;
  }

  // Wraps a block in <div align> for a center/end Alignment mark when htmlVisualMarks is on.
  String alignmentWrap(String body, List<AdfMark> marks, boolean htmlVisualMarks) {
    if (!htmlVisualMarks || body.isBlank()) {
      return body;
    }
    for (var mark : marks) {
      if (mark instanceof Alignment alignment) {
        var align =
            switch (alignment.align()) {
              case "center" -> "center";
              case "end" -> "right";
              case null, default -> null;
            };
        if (align != null) {
          // Blank lines around the body so CommonMark parses it as markdown, not a raw-HTML block.
          return "<div align=\"" + align + "\">\n\n" + body + "\n\n</div>";
        }
      }
    }
    return body;
  }

  // A run of non-breaking spaces for the block's first Indentation mark that indents (level clamped
  // to 6), or "" when there is none.
  String indentationPrefix(List<AdfMark> marks) {
    for (var mark : marks) {
      if (mark instanceof Indentation indentation && indentation.level() > 0) {
        return INDENT_UNIT.repeat(Math.min(indentation.level(), 6));
      }
    }
    return "";
  }

  // Wraps value's non-whitespace core with the open/close affixes, leaving any surrounding
  // whitespace outside; a blank value is returned unchanged.
  private String wrap(String value, String open, String close) {
    if (value.isBlank()) {
      return value;
    }

    var leadingWhitespaceLength = value.length() - value.stripLeading().length();
    var contentEnd = value.stripTrailing().length();
    return value.substring(0, leadingWhitespaceLength)
        + open
        + value.substring(leadingWhitespaceLength, contentEnd)
        + close
        + value.substring(contentEnd);
  }
}
