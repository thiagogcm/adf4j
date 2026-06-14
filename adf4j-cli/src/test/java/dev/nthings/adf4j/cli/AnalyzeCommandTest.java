package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalyzeCommandTest {

  @Test
  void defaultJsonEmitsEverySection() {
    var result = run(SIMPLE_DOC, "analyze");
    assertThat(result.exitCode()).isZero();
    for (var key : JsonRenderer.METADATA_KEYS) {
      assertThat(result.out()).contains("\"" + key + "\"");
    }
    assertThat(result.out()).contains("\"text\" : \"Hello\"");
  }

  @Test
  void selectLimitsToChosenSectionsCommaSeparated() {
    var result = run(SIMPLE_DOC, "analyze", "--select", "outline,referencedFileIds");
    assertThat(result.out()).contains("\"outline\"").contains("\"referencedFileIds\"");
    assertThat(result.out()).doesNotContain("\"pageRefs\"").doesNotContain("\"mentionRefs\"");
  }

  @Test
  void unknownSelectSectionIsAUsageError() {
    var result = run(SIMPLE_DOC, "analyze", "--select", "bogus");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("unknown section 'bogus'");
  }

  @Test
  void textFormatIsHumanReadable() {
    var result = run(SIMPLE_DOC, "analyze", "-f", "text", "--select", "outline");
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).contains("outline:").contains("H1 Hello");
  }
}
