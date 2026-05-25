package dev.nthings.adf4j.renderer;

import java.util.function.Consumer;

import dev.nthings.adf4j.model.BlockStyles;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

final class HtmlFragments {

  private HtmlFragments() {
  }

  static String paragraph(String html, BlockStyles blockStyles) {
    if (html == null || html.isBlank()) {
      return html != null ? html : "";
    }

    var element = new Element(Tag.valueOf("p"), "");
    applyStyles(element, blockStyles);
    element.html(toHtmlLineBreaks(html));
    return outerHtml(element);
  }

  static String heading(int level, String html, String anchor, BlockStyles blockStyles) {
    var element = new Element(Tag.valueOf("h" + level), "");
    if (anchor != null && !anchor.isBlank()) {
      element.attr("id", anchor);
    }
    applyStyles(element, blockStyles);
    element.html(toHtmlLineBreaks(html));
    return outerHtml(element);
  }

  static String anchor(String anchor) {
    if (anchor == null || anchor.isBlank()) {
      return "";
    }

    var element = new Element(Tag.valueOf("a"), "");
    element.attr("id", anchor);
    return outerHtml(element);
  }

  static String image(String source, String alt, Consumer<Element> customizer) {
    var element = new Element(Tag.valueOf("img"), "");
    element.attr("src", source);
    element.attr("alt", alt);
    if (customizer != null) {
      customizer.accept(element);
    }
    return outerHtml(element);
  }

  static String outerHtml(Element element) {
    var document = Document.createShell("");
    document.outputSettings(new Document.OutputSettings().prettyPrint(false));
    document.body().appendChild(element);
    return document.body().html();
  }

  private static void applyStyles(Element element, BlockStyles blockStyles) {
    if (blockStyles != null && blockStyles.hasStyles()) {
      element.attr("style", blockStyles.toInlineCss());
    }
  }

  private static String toHtmlLineBreaks(String value) {
    return value.replace("  \n", "\n\n").replace("\n\n", "<br>").replace("\n", "<br>");
  }
}
