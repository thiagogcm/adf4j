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

  String applyMarks(String value, List<AdfMark> marks) {
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
      rendered = "`" + rendered + "`";
    } else {
      nonLinkMarks.sort(INLINE_MARK_ORDER);
      for (var mark : nonLinkMarks) {
        rendered = applyInlineMark(rendered, mark);
      }
    }

    if (linkMark != null) {
      var href = linkMark.href();
      if (href != null && !href.isBlank()) {
        var label = (rendered == null || rendered.isBlank()) ? href : rendered;
        var title = linkMark.title();
        rendered = (title == null || title.isBlank())
            ? "[%s](%s)".formatted(label, href)
            : "[%s](%s \"%s\")".formatted(label, href, escapeLinkTitle(title));
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

  private boolean isVisualOnlyHtmlMark(AdfMark mark) {
    return switch (mark) {
      case Underline _,SubSup _,TextColor _,BackgroundColor _,Border _,FontSize _ -> true;
      case Alignment _,Annotation _,Breakout _,Code _,DataConsumer _,Em _,Fragment _,Indentation _,Link _,Strike _,Strong _,UnknownMark _ ->
        false;
    };
  }

  private String applyInlineMark(String value, AdfMark mark) {
    // Visual-only marks (colour, size, underline, …) carry no Markdown equivalent and are dropped.
    if (isVisualOnlyHtmlMark(mark)) {
      return value;
    }

    return switch (mark) {
      case Strong _ -> wrapDelimited(value, "**");
      case Em _ -> wrapDelimited(value, "*");
      case Strike _ -> wrapDelimited(value, "~~");
      case Underline _,SubSup _,TextColor _,BackgroundColor _,Border _,FontSize _,Alignment _,Indentation _,Annotation _,Fragment _,DataConsumer _,Breakout _,Link _,Code _,UnknownMark _ ->
        value;
    };
  }

  private String wrapDelimited(String value, String delimiter) {
    if (value.isBlank()) {
      return value;
    }

    var leadingWhitespaceLength = value.length() - value.stripLeading().length();
    var trailingWhitespaceLength = value.length() - value.stripTrailing().length();
    var contentEnd = value.length() - trailingWhitespaceLength;
    var leadingWhitespace = value.substring(0, leadingWhitespaceLength);
    var trailingWhitespace = value.substring(contentEnd);
    var content = value.substring(leadingWhitespaceLength, contentEnd);
    if (content.isBlank()) {
      return value;
    }

    return leadingWhitespace + delimiter + content + delimiter + trailingWhitespace;
  }

  private String escapeLinkTitle(String title) {
    return title.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
