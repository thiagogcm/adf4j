package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValidateCommandTest {

  // A valid root whose only diagnostic is an UNSUPPORTED_VERSION warning.
  private static final String UNSUPPORTED_VERSION_DOC =
      "{\"version\":999,\"type\":\"doc\",\"content\":[]}";

  // Not a doc root -> not a valid ADF root.
  private static final String INVALID_ROOT_DOC = "{\"version\":1,\"type\":\"paragraph\"}";

  @Test
  void validDocumentExitsZeroWithTextReport() {
    var result = run(SIMPLE_DOC, "validate");
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).contains("valid: true").contains("diagnostics: none");
  }

  @Test
  void invalidRootExitsContentFailure() {
    var result = run(INVALID_ROOT_DOC, "validate");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.out()).contains("valid: false");
  }

  @Test
  void textReportListsDiagnostics() {
    var result = run(INVALID_ROOT_DOC, "validate");
    assertThat(result.out()).contains("diagnostics:\n  [ERROR] INVALID_ROOT_NODE: ");
  }

  @Test
  void jsonFormatEmitsValidFlagAndDiagnostics() {
    var result = run(SIMPLE_DOC, "validate", "-f", "json");
    assertThat(result.out()).contains("\"valid\" : true").contains("\"diagnostics\"");
  }

  @Test
  void failOnWarningExitsFourWhenWarningPresent() {
    var withGate = run(UNSUPPORTED_VERSION_DOC, "validate", "--fail-on-warning");
    assertThat(withGate.exitCode()).isEqualTo(ExitCodes.QUALITY_GATE);

    var withoutGate = run(UNSUPPORTED_VERSION_DOC, "validate");
    assertThat(withoutGate.exitCode()).isZero();
    assertThat(withoutGate.out()).contains("valid: true");
  }
}
