package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

final class TextMarkRenderer {

  private static final Comparator<AdfMark> INLINE_MARK_ORDER = Comparator.comparingInt(TextMarkRenderer::canonicalRank);

  String applyMarks(String value, List<AdfMark> marks, boolean htmlVisualMarks) {
    if (marks == null || marks.isEmpty()) {
      return value;
    }

    Link linkMark = null;
    var codeMark = false;
    var nonLinkMarks = new ArrayList<AdfMark>();

    for (var mark : marks) {
      if (mark instanceof Link link) {
        linkMark = link;
      } else if (mark instanceof Code) {
        codeMark = true;
      } else if (!(mark instanceof UnknownMark)) {
        nonLinkMarks.add(mark);
      }
    }

    var rendered = value;

    if (codeMark) {
      rendered = wrapCodeSpan(rendered);
    } else {
      nonLinkMarks.sort(INLINE_MARK_ORDER);
      for (var mark : nonLinkMarks) {
        rendered = applyInlineMark(rendered, mark);
      }
      // Opt-in: preserve visual-only marks as one combined <span style> instead of dropping them.
      if (htmlVisualMarks) {
        var style = visualStyle(nonLinkMarks);
        if (!style.isEmpty()) {
          rendered = wrap(rendered, "<span style=\"" + style + "\">", "</span>");
        }
      }
    }

    if (linkMark != null) {
      var href = linkMark.href();
      if (href != null && !href.isBlank()) {
        var label = (rendered == null || rendered.isBlank()) ? MarkdownText.escapeInlineText(href, false) : rendered;
        var destination = MarkdownText.escapeUrlDestination(href);
        var title = linkMark.title();
        rendered = (title == null || title.isBlank())
            ? "[%s](%s)".formatted(label, destination)
            : "[%s](%s \"%s\")".formatted(label, destination, escapeLinkTitle(title));
      }
    }

    return rendered;
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

  private void addDeclaration(List<String> declarations, String property, String value) {
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
      case SubSup subSup -> wrapTag(value, "sup".equalsIgnoreCase(subSup.subSupType()) ? "sup" : "sub");
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

  private String escapeLinkTitle(String title) {
    return title.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  // Inline code span: the fence must exceed the longest backtick run in the content.
  private String wrapCodeSpan(String content) {
    var fence = "`".repeat(MarkdownText.longestBacktickRun(content) + 1);
    // Pad a space each side when content borders a backtick; CommonMark strips one space per side.
    var needsPadding = !content.isEmpty()
        && (content.charAt(0) == '`' || content.charAt(content.length() - 1) == '`');
    return needsPadding ? fence + " " + content + " " + fence : fence + content + fence;
  }
}
