package dev.nthings.adf4j.internal.render;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

final class HtmlFragments {

  private HtmlFragments() {
  }

  static String anchor(String anchor) {
    if (anchor == null || anchor.isBlank()) {
      return "";
    }

    var element = new Element(Tag.valueOf("a"), "");
    element.attr("id", anchor);
    return outerHtml(element);
  }

  static String outerHtml(Element element) {
    var document = Document.createShell("");
    document.outputSettings(new Document.OutputSettings().prettyPrint(false));
    document.body().appendChild(element);
    return document.body().html();
  }
}
