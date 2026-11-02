package dev.nthings.adf4j.testing;

import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/// Terse ADF JSON construction for tests that exercise the parser too. Every method returns a JSON
/// fragment; attribute maps are raw JSON objects.
public final class AdfSnippets {

  public static final String MACRO_TYPE = "com.atlassian.confluence.macro.core";

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private AdfSnippets() {}

  public static String doc(String... blocks) {
    return "{\"type\":\"doc\",\"version\":1,\"content\":" + array(blocks) + "}";
  }

  public static String node(String type, String attrs, String... content) {
    return "{\"type\":"
        + quote(type)
        + ",\"attrs\":"
        + attrs
        + ",\"content\":"
        + array(content)
        + "}";
  }

  public static String paragraph(String... inlines) {
    return node("paragraph", "{}", inlines);
  }

  public static String markedParagraph(String marks, String... inlines) {
    return "{\"type\":\"paragraph\",\"marks\":[" + marks + "],\"content\":" + array(inlines) + "}";
  }

  public static String heading(int level, String... inlines) {
    return node("heading", "{\"level\":" + level + "}", inlines);
  }

  public static String bullets(String... items) {
    var wrapped =
        List.of(items).stream().map(item -> node("listItem", "{}", paragraph(text(item))));
    return node("bulletList", "{}", wrapped.toArray(String[]::new));
  }

  public static String text(String value, String... marks) {
    return "{\"type\":\"text\",\"text\":" + quote(value) + ",\"marks\":" + array(marks) + "}";
  }

  public static String mark(String type) {
    return mark(type, "{}");
  }

  public static String mark(String type, String attrs) {
    return "{\"type\":" + quote(type) + ",\"attrs\":" + attrs + "}";
  }

  public static String link(String href) {
    return mark("link", "{\"href\":" + quote(href) + "}");
  }

  public static String atom(String type, String attrs) {
    return "{\"type\":" + quote(type) + ",\"attrs\":" + attrs + "}";
  }

  /// An extension node of the core macro namespace, with `params` as its `macroParams` values.
  public static String macro(String nodeType, String key, String params, String... content) {
    var attrs =
        "{\"extensionType\":"
            + quote(MACRO_TYPE)
            + ",\"extensionKey\":"
            + quote(key)
            + ",\"parameters\":{\"macroParams\":"
            + params
            + "}}";
    return content.length == 0 ? atom(nodeType, attrs) : node(nodeType, attrs, content);
  }

  public static String anchor(String id) {
    return macro("inlineExtension", "anchor", "{\"\":{\"value\":" + quote(id) + "}}");
  }

  public static String toc() {
    return macro("extension", "toc", "{}");
  }

  public static String quote(String value) {
    return MAPPER.writeValueAsString(value);
  }

  private static String array(String... items) {
    return List.of(items).stream().collect(Collectors.joining(",", "[", "]"));
  }
}
