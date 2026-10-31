package dev.nthings.adf4j.internal.parser;

import dev.nthings.adf4j.ast.Attributes;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/// Null/missing-safe readers for the structural fields the parser reads directly (`type`, `text`,
/// `version`). Attribute values go through {@link Attributes} instead.
final class JsonFields {

  private JsonFields() {}

  // The scalar's text, formatted as Attributes formats it, or fallback for an absent field or an
  // object/array.
  static String text(@Nullable JsonNode node, String field, String fallback) {
    var value = field(node, field);
    if (value == null || value.isContainer()) {
      return fallback;
    }
    return value.isNumber()
        ? new Attributes(Map.of(field, number(value))).text(field, fallback)
        : value.asString();
  }

  static Number number(JsonNode value) {
    return value.isIntegralNumber() ? value.bigIntegerValue() : value.decimalValue();
  }

  static int integer(@Nullable JsonNode node, String field, int fallback) {
    var value = field(node, field);
    return value == null ? fallback : value.asInt(fallback);
  }

  // node.field, or null when the node or field is absent/null/missing.
  static @Nullable JsonNode field(@Nullable JsonNode node, String field) {
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
