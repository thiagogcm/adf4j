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

/// Applies inline marks to text, and block marks (indentation, alignment) to whole blocks.
final class TextMarkRenderer {

  // NBSP per indent step: 4+ leading ASCII spaces would make a code block.
  private static final String INDENT_UNIT = "    ";

  // Colors and sizes only: none can end the declaration or attribute, or load anything.
  private static final Pattern SAFE_CSS_VALUE =
      Pattern.compile(
          "#\\p{XDigit}{3,8}"
              + "|\\p{Alpha}[\\p{Alpha}-]*"
              + "|\\d+(?:\\.\\d+)?(?:\\p{Alpha}+|%)?"
              + "|(?:rgb|hsl)a?\\([\\d.,%/ -]*\\)");

  private static final Pattern CSS_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

  /// A mark's effect on text, applied in `rank` order.
  private sealed interface MarkEffect {

    int rank();
  }

  private record Wrap(int rank, String open, String close) implements MarkEffect {}

  private record Css(int rank, String declaration) implements MarkEffect {}

  /// `pageNodeId` is what a `PageLinkResolver` looks up, so links that could resolve differently
  /// differ here without calling it.
  private record LinkTarget(String href, @Nullable String title, @Nullable String pageNodeId) {}

  /// Effective style: `effects` by rank (a later mark replaces an earlier one of the same rank).
  /// A code mark makes text literal and drops them. Equal styles render identically.
  private record Style(boolean code, Map<Integer, MarkEffect> effects, @Nullable LinkTarget link) {}

  // Inside-out: format marks (canonical order), visual <span>, then the link outermost.
  // `context` sits at the node carrying `marks`.
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

  /// Whether runs with these marks render alike and so merge. Calls no resolver: different hrefs
  /// stay apart even if a `PageLinkResolver` maps them to one page.
  boolean rendersAlike(List<AdfMark> left, List<AdfMark> right, RenderSession session) {
    return left.equals(right) || style(left, session).equals(style(right, session));
  }

  /// Whether a visual mark leaves no trace: always without `htmlVisualMarks`, else when its value
  /// is unsafe. Alignment is a block mark, omitted only without `htmlVisualMarks`.
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
      // Unknown subsup type stays unwrapped, not guessed. Code and links are not formatting
      // (see style); the rest never touch the text.
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

  // Pointer of the effective (last) link mark, reported with the resolver call.
  private static String linkPath(List<AdfMark> marks, RenderContext context) {
    var index = marks.size() - 1;
    while (index > 0 && !(marks.get(index) instanceof Link)) {
      index--;
    }
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
    // Opt-in: visual-only marks become one combined <span style>, not dropped.
    if (!declarations.isEmpty()) {
      var style = String.join("; ", declarations);
      rendered = wrap(rendered, "<span style=\"" + style + "\">", "</span>");
    }
    return rendered;
  }

  // A PageLinkResolver rewrites the destination only; the visible label is untouched (the href
  // when the text is blank).
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

  // Values are untrusted ADF JSON: anything outside a known-safe shape drops the declaration.
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

  // <div align> for center/end alignment, when htmlVisualMarks is on.
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
          // Blank lines let CommonMark parse the body as markdown, not raw HTML.
          return "<div align=\"" + align + "\">\n\n" + body + "\n\n</div>";
        }
      }
    }
    return body;
  }

  // NBSP run for the first indenting Indentation mark (level clamped to 6), else "".
  String indentationPrefix(List<AdfMark> marks) {
    for (var mark : marks) {
      if (mark instanceof Indentation indentation && indentation.level() > 0) {
        return INDENT_UNIT.repeat(Math.min(indentation.level(), 6));
      }
    }
    return "";
  }

  // Wraps the non-whitespace core; surrounding whitespace stays outside. Blank is unchanged.
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
