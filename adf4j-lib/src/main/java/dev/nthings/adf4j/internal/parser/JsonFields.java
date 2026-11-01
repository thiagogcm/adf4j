package dev.nthings.adf4j.internal.parser;

import dev.nthings.adf4j.ast.Attributes;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/// Readers for the structural JSON values the parser reads directly (`type`, `text`). Attribute
/// values go through {@link Attributes} instead.
final class JsonFields {

  private JsonFields() {}

  // The scalar's text, formatted as Attributes formats it, or "" for an absent or null field or an
  // object/array.
  static String text(JsonNode node, String field) {
    var value = node.get(field);
    if (value == null || value.isNull() || value.isContainer()) {
      return "";
    }
    return value.isNumber()
        ? new Attributes(Map.of(field, number(value))).text(field, "")
        : value.asString();
  }

  static Number number(JsonNode value) {
    return value.isIntegralNumber() ? value.bigIntegerValue() : value.decimalValue();
  }
}
