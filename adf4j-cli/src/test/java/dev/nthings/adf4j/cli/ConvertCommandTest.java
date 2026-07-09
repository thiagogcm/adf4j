package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.LOSSY_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.UNKNOWN_NODE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.convert;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConvertCommandTest {

  @Test
  void convertsStdinToBodyOnStdout() {
    var result = convert(SIMPLE_DOC);
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).isEqualTo("# Hello\n\nWorld");
  }

  @Test
  void readsFromFilePositional(@TempDir Path dir) {
    var input = CliTestSupport.write(dir, "doc.json", SIMPLE_DOC);
    var result = convert("", input.toString());
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).isEqualTo("# Hello\n\nWorld");
  }

  @Test
  void titleAndRenderingFlagsApply() {
    var result = convert(SIMPLE_DOC, "-t", "My Page");
    assertThat(result.out()).startsWith("# My Page\n");
  }

  @Test
  void jsonFormatEmitsFullResultPrettyByDefault() {
    var result = convert(SIMPLE_DOC, "-f", "json");
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).contains("\n  \"body\""); // pretty-printed (indented)
    assertThat(result.out()).contains("\"wasLossy\" : false");
    assertThat(result.out()).contains("\"diagnostics\"");
    assertThat(result.out()).contains("\"metadata\"");
    assertThat(result.out()).contains("\"unresolved\"");
    assertThat(result.out()).contains("\"outline\"");
  }

  @Test
  void compactFlagProducesSingleLineJson() {
    var result = convert(SIMPLE_DOC, "-f", "json", "--compact");
    assertThat(result.out().strip().lines().count()).isEqualTo(1);
    assertThat(result.out()).contains("\"body\":\"# Hello");
  }

  @Test
  void writesToOutputFileLeavingStdoutEmpty(@TempDir Path dir) throws Exception {
    var out = dir.resolve("out.md");
    var result = convert(SIMPLE_DOC, "-o", out.toString());
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).isEmpty();
    assertThat(Files.readString(out)).isEqualTo("# Hello\n\nWorld");
  }

  @Test
  void diagnosticsSummaryGoesToStderrNotStdout() {
    var result = convert(LOSSY_DOC, "-f", "json");
    assertThat(result.out()).doesNotContain("warning(s)");
    assertThat(result.err()).contains("warning(s)").contains("lossy=true");
  }

  @Test
  void quietSuppressesTheSummary() {
    var result = convert(LOSSY_DOC, "-q");
    assertThat(result.err()).isEmpty();
  }

  @Test
  void malformedAdfStillExitsZeroByDefault() {
    var result = convert("{ not json");
    assertThat(result.exitCode()).isZero(); // conversion never throws on bad input
  }

  @Test
  void failOnLossyExitsFourWhenLossy() {
    var result = convert(LOSSY_DOC, "--fail-on-lossy");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.QUALITY_GATE);
  }

  @Test
  void failOnLossyExitsZeroWhenClean() {
    var result = convert(SIMPLE_DOC, "--fail-on-lossy");
    assertThat(result.exitCode()).isZero();
  }

  @Test
  void unknownNodesFailAbortsWithContentFailure() {
    var result = convert(UNKNOWN_NODE_DOC, "--unknown-nodes", "fail");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.err()).contains("error:");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void invalidEnumValueIsAUsageError() {
    var result = convert(SIMPLE_DOC, "--unknown-nodes", "bogus");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("--unknown-nodes").contains("Allowed values");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void jsonDiagnosticsNeverLeakStackFramesOrCause() {
    var result = convert("{ definitely not valid json", "-f", "json");
    assertThat(result.out()).contains("INVALID_JSON");
    assertThat(result.out()).doesNotContain("\tat "); // no stack frames
    assertThat(result.out()).doesNotContain("Exception");
  }
}
