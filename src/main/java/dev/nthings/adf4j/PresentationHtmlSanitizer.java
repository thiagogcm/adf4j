package dev.nthings.adf4j;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.safety.Safelist;

final class PresentationHtmlSanitizer {

  private static final String BASE_URI = "https://adf4j.invalid/";

  private static final Safelist SAFE_HTML = new Safelist()
      .addTags(
          "a",
          "blockquote",
          "br",
          "code",
          "del",
          "div",
          "em",
          "h1",
          "h2",
          "h3",
          "h4",
          "h5",
          "h6",
          "hr",
          "img",
          "input",
          "li",
          "ol",
          "p",
          "pre",
          "span",
          "strong",
          "sub",
          "sup",
          "table",
          "tbody",
          "td",
          "th",
          "thead",
          "tr",
          "u",
          "ul")
      .addAttributes("a", "href", "id", "title")
      .addAttributes("div", "class", "data-alert-type")
      .addAttributes("h1", "id", "style")
      .addAttributes("h2", "id", "style")
      .addAttributes("h3", "id", "style")
      .addAttributes("h4", "id", "style")
      .addAttributes("h5", "id", "style")
      .addAttributes("h6", "id", "style")
      .addAttributes("img", "alt", "data-layout", "height", "src", "style", "title", "width")
      .addAttributes("input", "checked", "disabled", "type")
      .addAttributes("p", "class", "style")
      .addAttributes("span", "style")
      .addAttributes("td", "colspan", "rowspan", "style")
      .addAttributes("th", "colspan", "rowspan", "style")
      .addProtocols("a", "href", "#", "attachment", "http", "https", "mailto")
      .addProtocols("img", "src", "data", "http", "https", "media")
      .preserveRelativeLinks(true);

  String sanitize(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }

    var outputSettings = new OutputSettings().prettyPrint(false);
    return Jsoup.clean(html, BASE_URI, SAFE_HTML, outputSettings);
  }
}
