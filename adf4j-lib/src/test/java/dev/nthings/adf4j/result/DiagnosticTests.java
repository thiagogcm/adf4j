package dev.nthings.adf4j.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.nthings.adf4j.result.Diagnostic.Code;
import dev.nthings.adf4j.result.Diagnostic.Severity;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DiagnosticTests {

  private static final Map<Code, Severity> SEVERITIES =
      Map.ofEntries(
          Map.entry(Code.INVALID_JSON, Severity.ERROR),
          Map.entry(Code.INVALID_ROOT_TYPE, Severity.ERROR),
          Map.entry(Code.INVALID_ROOT_NODE, Severity.ERROR),
          Map.entry(Code.INVALID_VERSION, Severity.ERROR),
          Map.entry(Code.INVALID_CONTENT, Severity.ERROR),
          Map.entry(Code.UNSUPPORTED_VERSION, Severity.WARNING),
          Map.entry(Code.UNPRESERVED_JSON, Severity.WARNING),
          Map.entry(Code.UNKNOWN_NODE_PLACEHOLDER, Severity.WARNING),
          Map.entry(Code.UNKNOWN_NODE_SKIPPED, Severity.WARNING),
          Map.entry(Code.UNKNOWN_NODE_PRESERVED, Severity.INFO),
          Map.entry(Code.UNKNOWN_MARK_DROPPED, Severity.WARNING),
          Map.entry(Code.UNSUPPORTED_MACRO, Severity.WARNING));

  @Test
  void every_code_has_its_documented_severity() {
    var actual = new EnumMap<Code, Severity>(Code.class);
    for (var code : Code.values()) {
      actual.put(code, code.severity());
    }

    assertThat(actual).isEqualTo(SEVERITIES);
  }

  @ParameterizedTest
  @EnumSource(Code.class)
  void a_diagnostic_takes_its_severity_from_its_code(Code code) {
    assertThat(new Diagnostic(code, "message").severity()).isEqualTo(code.severity());
  }

  @Test
  void the_two_argument_constructor_has_no_cause() {
    assertThat(new Diagnostic(Code.UNSUPPORTED_MACRO, "message"))
        .isEqualTo(new Diagnostic(Code.UNSUPPORTED_MACRO, "message", null));
  }

  @Test
  void a_code_is_required() {
    assertThatNullPointerException().isThrownBy(() -> new Diagnostic(null, "message"));
  }

  @Test
  void only_warnings_and_errors_are_lossy() {
    assertThat(result(Code.UNKNOWN_NODE_PRESERVED).wasLossy()).isFalse();
    assertThat(result(Code.UNKNOWN_MARK_DROPPED).wasLossy()).isTrue();
    assertThat(result(Code.INVALID_JSON).wasLossy()).isTrue();
  }

  private static MarkdownResult result(Code code) {
    return new MarkdownResult("", null, List.of(new Diagnostic(code, "message")), null);
  }
}
