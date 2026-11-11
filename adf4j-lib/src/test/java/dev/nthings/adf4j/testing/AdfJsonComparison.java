package dev.nthings.adf4j.testing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Canonical form for the `AdfJson` lossless contract: keys sorted, numbers compared by value,
/// null-valued keys dropped, and an empty `attrs`, `content` or `marks` on a node treated as
/// absent. Inside `attrs` values an empty container is data, so it stays.
public final class AdfJsonComparison {

  private static final Set<String> NODE_KEYS = Set.of("attrs", "content", "marks");

  public static final JsonMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

  private AdfJsonComparison() {}

  public static String canonical(String json) {
    return node(MAPPER.readTree(json));
  }

  private static String node(JsonNode node) {
    if (node.isArray()) {
      var items = new ArrayList<String>();
      node.forEach(child -> items.add(node(child)));
      return items.toString();
    }
    if (!node.isObject()) {
      return value(node);
    }
    var fields = new TreeMap<String, String>();
    for (var property : node.properties()) {
      var key = property.getKey();
      var value = property.getValue();
      if (value.isNull()) {
        continue;
      }
      if (!NODE_KEYS.contains(key)) {
        fields.put(key, value(value));
      } else if (key.equals("attrs") && value.isObject()) {
        var attrs = value(value);
        if (!attrs.equals("{}")) {
          fields.put(key, attrs);
        }
      } else if (!(value.isContainer() && value.isEmpty())) {
        fields.put(key, node(value));
      }
    }
    return fields.toString();
  }

  // Not toPlainString: an exponent in the billions would need billions of digits.
  private static String canonicalNumber(BigDecimal number) {
    try {
      return number.stripTrailingZeros().toString();
    } catch (ArithmeticException _) {
      return number.toString();
    }
  }

  private static String value(JsonNode node) {
    if (node.isObject()) {
      var fields = new TreeMap<String, String>();
      for (var property : node.properties()) {
        if (!property.getValue().isNull()) {
          fields.put(property.getKey(), value(property.getValue()));
        }
      }
      return fields.toString();
    }
    if (node.isArray()) {
      var items = new ArrayList<@Nullable String>();
      node.forEach(child -> items.add(value(child)));
      return items.toString();
    }
    if (node.isNumber()) {
      return canonicalNumber(node.decimalValue());
    }
    return node.toString();
  }
}
