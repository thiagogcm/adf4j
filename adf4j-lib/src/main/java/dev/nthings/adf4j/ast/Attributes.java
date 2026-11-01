package dev.nthings.adf4j.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Generic, immutable view over a node's raw ADF `attrs`, iterating in the source document's
/// attribute order. Every value is plain JSON at any depth: String, Boolean, Long, BigInteger
/// (beyond long), Double, BigDecimal (not exactly a double), or a nested `Map`/`List`. Other
/// numeric types are widened to these and anything else is rejected, so equal documents compare
/// equal. Map keys and values are never null, but a nested list may contain null elements so a JSON
/// `null` keeps its array index. This keeps the core AST free of any product-specific or
/// JSON-library types. Product layers such as the `confluence` package read their extras from here,
/// which keeps the dependency pointing from those layers to the AST and never the other way around.
public record Attributes(Map<String, Object> values) {

  private static final Attributes EMPTY = new Attributes(Map.of());

  public Attributes {
    values = normalizedMap(values);
  }

  public static Attributes empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  /// The value at `key` when it is a string, otherwise `null`.
  public @Nullable String string(String key) {
    return values.get(key) instanceof String value ? value : null;
  }

  /// The scalar at `key` as text (a number or boolean in its JSON form), or `null` when absent or
  /// not a scalar.
  public @Nullable String text(String key) {
    return switch (values.get(key)) {
      case String value -> value;
      case Number value -> value.toString();
      case Boolean value -> value.toString();
      case null, default -> null;
    };
  }

  /// {@link #text(String)}, or `fallback` when that is `null`.
  public String text(String key, String fallback) {
    var value = text(key);
    return value == null ? fallback : value;
  }

  /// The value at `key` as an int: a number truncated toward zero, or a string holding an integer.
  /// Anything else, including a number outside the int range, yields `fallback`.
  public int integer(String key, int fallback) {
    return switch (values.get(key)) {
      case Number value -> inIntRange(value.doubleValue()) ? value.intValue() : fallback;
      case String value -> parsedInt(value, fallback);
      case null, default -> fallback;
    };
  }

  /// The value at `key` as a boolean: a boolean, the string `true` or `false`, or an integer
  /// (non-zero is true). Anything else yields `fallback`.
  public boolean bool(String key, boolean fallback) {
    return switch (values.get(key)) {
      case Boolean value -> value;
      case Long value -> value != 0L;
      case BigInteger value -> value.signum() != 0;
      case String value when value.equals("true") -> true;
      case String value when value.equals("false") -> false;
      case null, default -> fallback;
    };
  }

  /// The nested object at `key`, or {@link #empty()} when absent or not an object.
  @SuppressWarnings("unchecked")
  public Attributes object(String key) {
    return values.get(key) instanceof Map<?, ?> nested
        ? new Attributes((Map<String, Object>) nested)
        : EMPTY;
  }

  // Compares before truncating, so 2147483647.5 is out of range.
  private static boolean inIntRange(double value) {
    return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
  }

  // ASCII digits only: Integer.parseInt alone also accepts other scripts' digits.
  private static int parsedInt(String value, int fallback) {
    try {
      return value.chars().allMatch(c -> c < 128) ? Integer.parseInt(value) : fallback;
    } catch (NumberFormatException _) {
      return fallback;
    }
  }

  // Not Map.copyOf: its per-JVM-run salted iteration order would lose the document order.
  private static Map<String, Object> normalizedMap(@Nullable Map<String, ?> values) {
    if (values == null || values.isEmpty()) {
      return Map.of();
    }
    if (values instanceof NormalizedMap normalized) {
      return normalized;
    }
    var copy = LinkedHashMap.<String, Object>newLinkedHashMap(values.size());
    for (var entry : values.entrySet()) {
      copy.put(
          Objects.requireNonNull(entry.getKey(), "key"),
          normalized(Objects.requireNonNull(entry.getValue(), "value")));
    }
    return new NormalizedMap(Collections.unmodifiableMap(copy));
  }

  // Marks a map as deeply normalized, so reading a nested object does not copy it again.
  private static final class NormalizedMap extends AbstractMap<String, Object> {

    private final Map<String, Object> values;

    NormalizedMap(Map<String, Object> values) {
      this.values = values;
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
      return values.entrySet();
    }

    @Override
    public @Nullable Object get(Object key) {
      return values.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
      return values.containsKey(key);
    }
  }

  private static List<@Nullable Object> normalizedList(List<?> values) {
    var copy = new ArrayList<@Nullable Object>(values.size());
    for (var value : values) {
      copy.add(value == null ? null : normalized(value));
    }
    return Collections.unmodifiableList(copy);
  }

  @SuppressWarnings("unchecked")
  private static Object normalized(Object value) {
    return switch (value) {
      case String _, Boolean _, Long _ -> value;
      case Integer number -> number.longValue();
      case Short number -> number.longValue();
      case Byte number -> number.longValue();
      case BigInteger number -> number.bitLength() < Long.SIZE ? number.longValueExact() : number;
      case Double number -> finite(number);
      case Float number -> finite(Double.parseDouble(number.toString()));
      case BigDecimal number -> normalized(number);
      case Map<?, ?> nested -> normalizedMap((Map<String, ?>) nested);
      case List<?> nested -> normalizedList(nested);
      case Attributes nested -> nested.values();
      default ->
          throw new IllegalArgumentException("Not a JSON value: " + value.getClass().getName());
    };
  }

  // A double when it holds the value exactly, otherwise the decimal in its canonical scale.
  private static Object normalized(BigDecimal number) {
    var approximate = number.doubleValue();
    if (Double.isFinite(approximate)
        && new BigDecimal(Double.toString(approximate)).compareTo(number) == 0) {
      return approximate;
    }
    // At the scale limits the canonical form overflows, or is more than a JSON reader accepts, so
    // such a number stays as written.
    try {
      var canonical = number.stripTrailingZeros();
      return canonical.scale() == Integer.MIN_VALUE ? number : canonical;
    } catch (ArithmeticException _) {
      return number;
    }
  }

  private static double finite(double number) {
    if (!Double.isFinite(number)) {
      throw new IllegalArgumentException("Not a JSON number: " + number);
    }
    return number;
  }
}
