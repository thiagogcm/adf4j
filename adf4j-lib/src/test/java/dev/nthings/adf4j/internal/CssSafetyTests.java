package dev.nthings.adf4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CssSafetyTests {

  @Test
  void safe_values_are_trimmed_and_preserved() {
    assertThat(CssSafety.safeValue(" #ff5630 ")).isEqualTo("#ff5630");
    assertThat(CssSafety.safeValue("rebeccapurple")).isEqualTo("rebeccapurple");
    assertThat(CssSafety.safeValue("14px")).isEqualTo("14px");
    assertThat(CssSafety.safeValue("1.5rem")).isEqualTo("1.5rem");
    assertThat(CssSafety.safeValue("rgba(255, 0, 128, 0.5)")).isEqualTo("rgba(255, 0, 128, 0.5)");
  }

  @Test
  void unsafe_or_blank_values_are_rejected() {
    assertThat(CssSafety.safeValue(null)).isNull();
    assertThat(CssSafety.safeValue("   ")).isNull();
    assertThat(CssSafety.safeValue("red; background:url(https://evil.example)")).isNull();
    assertThat(CssSafety.safeValue("expression(alert(1))")).isNull();
    assertThat(CssSafety.safeValue("#12")).isNull();
    assertThat(CssSafety.safeValue("url(data:image/svg+xml,foo)")).isNull();
  }

  @Test
  void safe_number_accepts_non_negative_numbers_only() {
    assertThat(CssSafety.safeNumber(" 2 ")).isEqualTo("2");
    assertThat(CssSafety.safeNumber("1.5")).isEqualTo("1.5");
    assertThat(CssSafety.safeNumber(null)).isNull();
    assertThat(CssSafety.safeNumber("")).isNull();
    assertThat(CssSafety.safeNumber("-1")).isNull();
    assertThat(CssSafety.safeNumber("2px")).isNull();
  }
}
