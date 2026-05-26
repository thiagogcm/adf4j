package dev.nthings.adf4j.parser;

import tools.jackson.databind.JsonNode;

final class JsonFields {

  private JsonFields() {
  }

  static String text(JsonNode node, String field) {
    return text(node, field, null);
  }

  static String text(JsonNode node, String field, String fallback) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return fallback;
    }

    var value = node.get(field);
    if (value == null || value.isNull() || value.isMissingNode()) {
      return fallback;
    }

    return value.asString();
  }
}
