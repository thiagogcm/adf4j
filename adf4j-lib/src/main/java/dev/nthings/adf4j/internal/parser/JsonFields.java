package dev.nthings.adf4j.internal.parser;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/// Null/missing-safe readers for the field coercions the parser needs, funnelled through one place.
final class JsonFields {

  private JsonFields() {}

  static @Nullable String text(@Nullable JsonNode node, String field) {
    var value = field(node, field);
    return value == null ? null : value.asString();
  }

  static String text(@Nullable JsonNode node, String field, String fallback) {
    var value = field(node, field);
    return value == null ? fallback : value.asString();
  }

  static int integer(@Nullable JsonNode node, String field, int fallback) {
    var value = field(node, field);
    return value == null ? fallback : value.asInt(fallback);
  }

  static boolean bool(@Nullable JsonNode node, String field, boolean fallback) {
    var value = field(node, field);
    return value == null ? fallback : value.asBoolean(fallback);
  }

  // node.field, or null when the node or field is absent/null/missing.
  private static @Nullable JsonNode field(@Nullable JsonNode node, String field) {
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
