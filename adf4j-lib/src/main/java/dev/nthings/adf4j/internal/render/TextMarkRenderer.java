package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.Alignment;
import dev.nthings.adf4j.ast.Annotation;
import dev.nthings.adf4j.ast.Attributes;
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
import dev.nthings.adf4j.confluence.ConfluenceMetadata;
import dev.nthings.adf4j.internal.ConfluenceSupport;

import org.jspecify.annotations.Nullable;

final class TextMarkRenderer {

  private static final Comparator<AdfMark> INLINE_MARK_ORDER = Comparator.comparingInt(TextMarkRenderer::canonicalRank);

  String applyMarks(String value, @Nullable List<AdfMark> marks, RenderContext context) {
    if (marks == null || marks.isEmpty()) {
      return value;
    }
    return markDecorator(marks, context).apply(value);
  }

  // Inside-out decorator chain: format marks (canonical order) innermost, then an optional combined
  // visual <span>, then the link outermost. A code mark makes the text literal, superseding the
  // format/visual layers. identity().andThen(...) nests so the first decorator added wraps innermost.
  private Function<String, String> markDecorator(List<AdfMark> marks, RenderContext context) {
    var htmlVisualMarks = context.options().htmlVisualMarks();
    Link link = null;
    var hasCode = false;
    var formatMarks = new ArrayList<AdfMark>();
    for (var mark : marks) {
      switch (mark) {
        case Link found -> link = found;
        case Code _ -> hasCode = true;
        case UnknownMark _ -> { }
        default -> formatMarks.add(mark);
      }
    }

    Function<String, String> decorator = Function.identity();
    if (hasCode) {
      decorator = decorator.andThen(this::wrapCodeSpan);
    } else {
      formatMarks.sort(INLINE_MARK_ORDER);
      for (var mark : formatMarks) {
        decorator = decorator.andThen(text -> applyInlineMark(text, mark));
      }
      // Opt-in: preserve visual-only marks as one combined <span style> instead of dropping them.
      if (htmlVisualMarks) {
        var style = visualStyle(formatMarks);
        if (!style.isEmpty()) {
          var open = "<span style=\"" + style + "\">";
          decorator = decorator.andThen(text -> wrap(text, open, "</span>"));
        }
      }
    }
    if (link != null) {
      var linkMark = link;
      decorator = decorator.andThen(text -> applyLink(text, linkMark, context));
    }
    return decorator;
  }

  // Wraps formatted text in a Markdown link, or returns it unchanged when the link has no href. A
  // PageLinkResolver rewrites an internal page href to the caller's destination; the visible label
  // (the original text, or the original href when the text is blank) is left untouched.
  private String applyLink(@Nullable String rendered, Link link, RenderContext context) {
    var href = link.href();
    if (href == null || href.isBlank()) {
      return rendered;
    }
    var label = (rendered == null || rendered.isBlank())
        ? MarkdownText.escapeInlineText(href, false, context.options().escapeParentheses())
        : rendered;
    var resolvedHref = resolvePageHref(href, link.attrs(), context);
    var title = link.title();
    return (title == null || title.isBlank())
        ? MarkdownText.linkRendered(label, resolvedHref)
        : MarkdownText.linkRendered(label, resolvedHref, title);
  }

  // The caller-resolved destination for an internal page href, or the original href when there is no
  // PageLinkResolver, the href is not a page reference, or the resolver declines.
  static String resolvePageHref(String href, Attributes attrs, RenderContext context) {
    if (context.options().pageLinkResolver() == null) {
      return href;
    }
    var resolved =
        resolvePageId(ConfluenceSupport.pageNodeId(href, ConfluenceMetadata.from(attrs)), context);
    return resolved == null ? href : resolved;
  }

  // The resolver's destination for a page node id, or null when there is no id/resolver or it
  // declines; a decline is recorded as an unresolved page ref.
  static @Nullable String resolvePageId(@Nullable String pageNodeId, RenderContext context) {
    var resolver = context.options().pageLinkResolver();
    if (resolver == null || pageNodeId == null || pageNodeId.isBlank()) {
      return null;
    }
    var resolved = CallbackGuards.guardNonBlank("PageLinkResolver", () -> resolver.resolve(pageNodeId));
    if (resolved == null) {
      context.unresolvedTracker().recordPageId(pageNodeId);
    }
    return resolved;
  }

  private static int canonicalRank(AdfMark mark) {
    return switch (mark) {
      case Strong _ -> 0;
      case Em _ -> 1;
      case Strike _ -> 2;
      case SubSup _ -> 3;
      case Underline _ -> 4;
      case FontSize _ -> 5;
      case TextColor _ -> 6;
      case BackgroundColor _ -> 7;
      case Border _ -> 8;
      case Alignment _,Annotation _,Breakout _,Code _,DataConsumer _,Fragment _,Indentation _,Link _,UnknownMark _ ->
        99;
    };
  }

  // CSS for the visual-only marks, in canonical order, guarded against HTML-attribute breakout
  // (the colour/size values are arbitrary JSON). Empty when there are no visual marks.
  private String visualStyle(List<AdfMark> marks) {
    var declarations = new ArrayList<String>();
    for (var mark : marks) {
      switch (mark) {
        case FontSize fontSize -> addDeclaration(declarations, "font-size", fontSize.fontSize());
        case TextColor textColor -> addDeclaration(declarations, "color", textColor.color());
        case BackgroundColor backgroundColor ->
          addDeclaration(declarations, "background-color", backgroundColor.color());
        case Border border -> addBorderDeclaration(declarations, border);
        default -> { }
      }
    }
    return String.join("; ", declarations);
  }

  private void addDeclaration(List<String> declarations, String property, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      declarations.add(property + ":" + HtmlFragments.escapeHtmlText(value));
    }
  }

  private void addBorderDeclaration(List<String> declarations, Border border) {
    var size = border.size();
    var color = border.color();
    if (size == null || size.isBlank() || color == null || color.isBlank()) {
      return;
    }
    declarations.add(
        "border:" + HtmlFragments.escapeHtmlText(size) + "px solid " + HtmlFragments.escapeHtmlText(color));
  }

  private boolean isVisualOnlyHtmlMark(AdfMark mark) {
    return switch (mark) {
      case TextColor _,BackgroundColor _,Border _,FontSize _ -> true;
      case Alignment _,Annotation _,Breakout _,Code _,DataConsumer _,Em _,Fragment _,Indentation _,Link _,Strike _,Strong _,SubSup _,Underline _,UnknownMark _ ->
        false;
    };
  }

  private String applyInlineMark(String value, AdfMark mark) {
    // Pure-visual marks (colour/background/border/size) are dropped; sub/sup and underline map to HTML.
    if (isVisualOnlyHtmlMark(mark)) {
      return value;
    }

    return switch (mark) {
      case Strong _ -> wrapDelimited(value, "**");
      case Em _ -> wrapDelimited(value, "*");
      case Strike _ -> wrapDelimited(value, "~~");
      case SubSup s when "sup".equalsIgnoreCase(s.subSupType()) -> wrapTag(value, "sup");
      case SubSup s when "sub".equalsIgnoreCase(s.subSupType()) -> wrapTag(value, "sub");
      case SubSup _ -> value; // unknown subtype: leave unwrapped rather than guess
      case Underline _ -> wrapTag(value, "u");
      case TextColor _,BackgroundColor _,Border _,FontSize _,Alignment _,Indentation _,Annotation _,Fragment _,DataConsumer _,Breakout _,Link _,Code _,UnknownMark _ ->
        value;
    };
  }

  private String wrapDelimited(String value, String delimiter) {
    return wrap(value, delimiter, delimiter);
  }

  private String wrapTag(String value, String tag) {
    return wrap(value, "<" + tag + ">", "</" + tag + ">");
  }

  // Wraps value's non-whitespace core with the open/close affixes, leaving any surrounding
  // whitespace outside; a blank value is returned unchanged.
  private String wrap(String value, String open, String close) {
    if (value.isBlank()) {
      return value;
    }

    var leadingWhitespaceLength = value.length() - value.stripLeading().length();
    var contentEnd = value.stripTrailing().length();
    var content = value.substring(leadingWhitespaceLength, contentEnd);
    if (content.isBlank()) {
      return value;
    }

    return value.substring(0, leadingWhitespaceLength) + open + content + close
        + value.substring(contentEnd);
  }

  // Inline code span: the fence must exceed the longest backtick run in the content.
  private String wrapCodeSpan(String content) {
    return MarkdownText.inlineCodeSpan(content);
  }
}
