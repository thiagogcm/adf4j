package dev.nthings.adf4j.internal.parser;

import tools.jackson.databind.JsonNode;

/** Null/missing-safe readers for the field coercions the parser needs, funnelled through one place. */
final class JsonFields {

  private JsonFields() {
  }

  static String text(JsonNode node, String field) {
    return text(node, field, null);
  }

  static String text(JsonNode node, String field, String fallback) {
    var value = field(node, field);
    return value == null ? fallback : value.asString();
  }

  static int integer(JsonNode node, String field, int fallback) {
    var value = field(node, field);
    return value == null ? fallback : value.asInt(fallback);
  }

  static boolean bool(JsonNode node, String field, boolean fallback) {
    var value = field(node, field);
    return value == null ? fallback : value.asBoolean(fallback);
  }

  // node.field, or null when the node or field is absent/null/missing.
  private static JsonNode field(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    var value = node.get(field);
    if (value == null || value.isNull() || value.isMissingNode()) {
      return null;
    }
    return value;
  }
}
