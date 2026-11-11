package dev.nthings.adf4j.internal.parser;

import dev.nthings.adf4j.ast.Attributes;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/// Reads the structural values (`type`, `text`) directly. Attribute values go through
/// {@link Attributes}.
final class JsonFields {

  private JsonFields() {}

  // Scalar text as Attributes formats it; "" for absent, null or container.
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
