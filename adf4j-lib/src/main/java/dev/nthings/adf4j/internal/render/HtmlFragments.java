package dev.nthings.adf4j.internal.render;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.jspecify.annotations.Nullable;

final class HtmlFragments {

  private HtmlFragments() {}

  /// An empty `<a id>` target, or `""` without an id.
  static String anchor(@Nullable String anchor) {
    if (anchor == null) {
      return "";
    }

    var element = new Element(Tag.valueOf("a"), "");
    element.attr("id", anchor);
    return outerHtml(element);
  }

  /// Escapes the HTML text-significant characters so an ADF string is safe inside element text.
  static String escapeHtmlText(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  static String outerHtml(Element element) {
    var document = Document.createShell("");
    document.outputSettings(new Document.OutputSettings().prettyPrint(false));
    document.body().appendChild(element);
    return document.body().html();
  }
}
