package dev.nthings.adf4j.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AttributesTests {

  private static Attributes one(Object value) {
    return new Attributes(Map.of("k", value));
  }

  @Test
  void numbers_normalize_so_equal_documents_compare_equal() {
    assertThat(one(1)).isEqualTo(one(1L)).isEqualTo(one((short) 1)).isEqualTo(one((byte) 1));
    assertThat(one(BigInteger.TEN)).isEqualTo(one(10L));
    assertThat(one(new BigDecimal("1.50"))).isEqualTo(one(1.5));
    assertThat(one(1.5f)).isEqualTo(one(1.5));
    assertThat(one(new BigDecimal("1E+400"))).isEqualTo(one(new BigDecimal("10E+399")));
  }

  @Test
  void numbers_beyond_long_and_double_keep_every_digit() {
    var big = new BigInteger("99999999999999999999999");
    var decimal = new BigDecimal("1.2345678901234567890123456");

    assertThat(one(big).values().get("k")).isEqualTo(big);
    assertThat(one(decimal).values().get("k")).isEqualTo(decimal);
  }

  @Test
  void normalization_reaches_nested_values_and_copies_them() {
    var list = new ArrayList<Object>(Arrays.asList(1, null));
    var nested = new HashMap<String, Object>(Map.of("list", list));

    var attributes = one(nested);
    list.add(2);

    assertThat(attributes.object("k").values().get("list")).isEqualTo(Arrays.asList(1L, null));
  }

  @Test
  void nested_values_are_immutable_at_every_depth() {
    var attributes = one(Map.of("list", List.of(Map.of("deep", 1))));

    var object = attributes.object("k");
    var list = (List<?>) object.values().get("list");
    var nested = (Map<?, ?>) list.getFirst();

    assertThatThrownBy(() -> attributes.values().put("x", 1))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> attributes.values().entrySet().iterator().next().setValue("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> list.remove(0)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> nested.remove("deep"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void a_scale_at_the_int_limit_stays_as_written() {
    var overflowing = new BigDecimal("100E+2147483647");
    var atTheLimit = new BigDecimal("10E+2147483647");

    assertThat(one(overflowing).values().get("k")).isEqualTo(overflowing);
    assertThat(one(atTheLimit).values().get("k")).isEqualTo(atTheLimit);
  }

  @Test
  void non_json_values_are_rejected() {
    assertThatThrownBy(() -> one(new Object())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> one(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> one(Map.of("x", Double.POSITIVE_INFINITY)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> one(List.of(new Object())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void text_gives_a_scalar_in_its_json_form() {
    assertThat(one("s").text("k")).isEqualTo("s");
    assertThat(one(2).text("k")).isEqualTo("2");
    assertThat(one(1500.0).text("k")).isEqualTo("1500.0");
    assertThat(one(true).text("k")).isEqualTo("true");
    assertThat(one(Map.of()).text("k")).isNull();
    assertThat(one(List.of()).text("k")).isNull();
    assertThat(one(1).text("absent")).isNull();
    assertThat(one(List.of()).text("k", "fallback")).isEqualTo("fallback");
  }

  @Test
  void string_gives_only_a_string() {
    assertThat(one("s").string("k")).isEqualTo("s");
    assertThat(one(2).string("k")).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "2,2",
    "2.7,2",
    "-2.7,-2",
    "2147483647,2147483647",
    "2147483648,-7",
    "2147483647.5,-7",
    "-2147483649,-7",
    "1E400,-7",
    "1E-400,0",
    "99999999999999999999999,-7"
  })
  void integer_truncates_numbers_inside_the_int_range(BigDecimal number, int expected) {
    assertThat(one(number).integer("k", -7)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"2,2", "+2,2", "-2,-2", "007,7", "2.7,-7", "' 2',-7", "'',-7", "abc,-7", "٢,-7"})
  void integer_reads_a_string_holding_an_integer(String text, int expected) {
    assertThat(one(text).integer("k", -7)).isEqualTo(expected);
  }

  @Test
  void integer_ignores_other_values() {
    assertThat(one(true).integer("k", -7)).isEqualTo(-7);
    assertThat(one(Map.of()).integer("k", -7)).isEqualTo(-7);
    assertThat(Attributes.empty().integer("k", -7)).isEqualTo(-7);
  }

  @Test
  void bool_reads_booleans_exact_strings_and_integers() {
    assertThat(one(true).bool("k", false)).isTrue();
    assertThat(one("true").bool("k", false)).isTrue();
    assertThat(one("false").bool("k", true)).isFalse();
    assertThat(one(1).bool("k", false)).isTrue();
    assertThat(one(0).bool("k", true)).isFalse();
    assertThat(one(new BigInteger("99999999999999999999999")).bool("k", false)).isTrue();
    assertThat(one("True").bool("k", false)).isFalse();
    assertThat(one(2.5).bool("k", false)).isFalse();
    assertThat(one(2.5).bool("k", true)).isTrue();
    assertThat(Attributes.empty().bool("k", true)).isTrue();
  }

  @Test
  void number_reads_numbers_and_numeric_strings() {
    var attributes =
        new Attributes(
            Map.of("a", 33.33, "b", "50", "c", "NaN", "d", true, "e", "1e400", "f", "٥٠"));

    assertThat(attributes.number("a", 0)).isEqualTo(33.33);
    assertThat(attributes.number("b", 0)).isEqualTo(50);
    assertThat(attributes.number("c", 7)).isEqualTo(7);
    assertThat(attributes.number("d", 7)).isEqualTo(7);
    assertThat(attributes.number("e", 7)).isEqualTo(7);
    assertThat(attributes.number("f", 7)).isEqualTo(7);
    assertThat(attributes.number("missing", 7)).isEqualTo(7);
  }
}
