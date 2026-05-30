package dev.nthings.adf4j.renderer;

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
import dev.nthings.adf4j.model.BlockStyles;

record ResolvedLink(String href, String label) {
}

@FunctionalInterface
interface LinkTargetResolver {
  ResolvedLink resolve(String href, String renderedLabel);
}

public final class TextMarkRenderer {

  private static final Comparator<AdfMark> INLINE_MARK_ORDER = Comparator.comparingInt(TextMarkRenderer::canonicalRank);

  String applyMarks(
      String value, List<AdfMark> marks, RenderingStrategy strategy, LinkTargetResolver linkResolver) {
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
        rendered = applyInlineMark(rendered, mark, strategy);
      }
    }

    if (linkMark != null) {
      var href = linkMark.href();
      if (href != null && !href.isBlank()) {
        var resolvedLink = linkResolver == null ? null : linkResolver.resolve(href, rendered);
        var finalHref = resolvedLink == null || resolvedLink.href() == null || resolvedLink.href().isBlank()
            ? href
            : resolvedLink.href();
        var fallbackLabel = (rendered == null || rendered.isBlank()) ? finalHref : rendered;
        var label = resolvedLink == null
            ? fallbackLabel
            : (resolvedLink.label() == null || resolvedLink.label().isBlank())
                ? fallbackLabel
                : resolvedLink.label();
        var title = linkMark.title();
        rendered = (title == null || title.isBlank())
            ? "[%s](%s)".formatted(label, finalHref)
            : "[%s](%s \"%s\")".formatted(label, finalHref, escapeLinkTitle(title));
      }
    }

    return rendered;
  }

  BlockStyles extractBlockStyles(List<AdfMark> marks) {
    if (marks == null || marks.isEmpty()) {
      return BlockStyles.none();
    }

    String alignment = null;
    Integer indentationLevel = null;
    String fontSize = null;

    for (var mark : marks) {
      switch (mark) {
        case Alignment a -> {
          var align = a.align();
          if ("center".equals(align) || "end".equals(align)) {
            alignment = align;
          }
        }
        case Indentation indent -> {
          if (indent.level() > 0) {
            indentationLevel = Math.clamp(indent.level(), 1, 6);
          }
        }
        case FontSize size -> {
          if (size.fontSize() != null && !size.fontSize().isBlank()) {
            fontSize = size.fontSize();
          }
        }
        default -> {
        }
      }
    }

    return new BlockStyles(alignment, indentationLevel, fontSize);
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

  private String applyInlineMark(String value, AdfMark mark, RenderingStrategy strategy) {
    if (mark instanceof UnknownMark) {
      return value;
    }

    if (strategy.omitsVisualOnlyMarks() && isVisualOnlyHtmlMark(mark)) {
      return value;
    }

    return switch (mark) {
      case Strong _ -> wrapDelimited(value, "**");
      case Em _ -> wrapDelimited(value, "*");
      case Strike _ -> wrapDelimited(value, "~~");
      case Underline _ -> "<u>" + value + "</u>";
      case SubSup s -> applySubsupMark(value, s.subSupType());
      case TextColor c -> applySpanMark(value, "color", c.color());
      case BackgroundColor c -> applySpanMark(value, "background-color", c.color());
      case Border b -> applyBorderMark(value, b);
      case FontSize f -> applySpanMark(value, "font-size", f.fontSize());
      case Alignment _,Indentation _,Annotation _,Fragment _,DataConsumer _,Breakout _,Link _,Code _,UnknownMark _ ->
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

  private String applyBorderMark(String value, Border border) {
    var color = border.color() == null || border.color().isBlank() ? "currentColor" : border.color();
    var size = border.size() == null || border.size().isBlank() ? "1" : border.size();
    var parsedSize = "1";
    try {
      parsedSize = Integer.toString(Math.clamp(Integer.parseInt(size), 1, 3));
    } catch (NumberFormatException _) {
      parsedSize = "1";
    }

    return "<span style=\"border:%spx solid %s\">%s</span>".formatted(parsedSize, color, value);
  }

  private String applySubsupMark(String value, String subSupType) {
    if ("sub".equals(subSupType)) {
      return "<sub>" + value + "</sub>";
    }
    if ("sup".equals(subSupType)) {
      return "<sup>" + value + "</sup>";
    }
    return value;
  }

  private String applySpanMark(String value, String property, String cssValue) {
    if (cssValue == null || cssValue.isBlank()) {
      return value;
    }

    return "<span style=\"%s:%s\">%s</span>".formatted(property, cssValue, value);
  }

  private String escapeLinkTitle(String title) {
    return title.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
