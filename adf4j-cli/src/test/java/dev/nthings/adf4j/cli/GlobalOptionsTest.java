package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.convert;
import static dev.nthings.adf4j.cli.CliTestSupport.run;
import static dev.nthings.adf4j.cli.CliTestSupport.runNoInput;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobalOptionsTest {

  @Test
  void versionLongAndShortPrintTheVersion() {
    assertThat(Cli.VERSION).isNotBlank().isNotEqualTo("unknown");
    assertThat(runNoInput("--version").out()).isEqualTo("adf4j " + Cli.VERSION + "\n");
    assertThat(runNoInput("-V").out()).isEqualTo("adf4j " + Cli.VERSION + "\n");
  }

  @Test
  void versionWorksPerSubcommand() {
    assertThat(runNoInput("convert", "-V").out()).isEqualTo("adf4j " + Cli.VERSION + "\n");
    assertThat(runNoInput("analyze", "--version").out()).isEqualTo("adf4j " + Cli.VERSION + "\n");
  }

  @Test
  void lowercaseVIsVerboseNotVersion() {
    // -v is verbose, not version (case-sensitive short flags)
    var result = convert(SIMPLE_DOC, "-v");
    assertThat(result.out()).isEqualTo("# Hello\n\nWorld");
  }

  @Test
  void verboseAddsStackTraceOnError() {
    var withoutVerbose = convert(SIMPLE_DOC, "--media-map", "/no/such/file.json");
    var withVerbose = convert(SIMPLE_DOC, "-v", "--media-map", "/no/such/file.json");
    assertThat(withoutVerbose.err()).doesNotContain("\tat ");
    assertThat(withVerbose.err()).contains("\tat ");
  }

  @Test
  void noArgsPrintsGlobalHelp() {
    var result = runNoInput();
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).contains("Commands:");
  }

  @Test
  void helpLongAndShortPrintGlobalUsage() {
    assertThat(runNoInput("--help").out()).contains("adf4j <command>").contains("Commands:");
    assertThat(runNoInput("-h").out()).contains("Commands:");
  }

  @Test
  void perCommandHelp() {
    assertThat(runNoInput("convert", "--help").out()).contains("adf4j convert");
    assertThat(runNoInput("analyze", "--help").out()).contains("adf4j analyze");
    assertThat(runNoInput("validate", "--help").out()).contains("adf4j validate");
  }

  @Test
  void unknownCommandIsAUsageError() {
    var result = run(SIMPLE_DOC, "frobnicate");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("unknown command 'frobnicate'");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void unknownOptionIsAUsageError() {
    var result = convert(SIMPLE_DOC, "--bogus");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("unknown option '--bogus'");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void doubleDashEndsOptionParsingWithinACommand() {
    // `convert -- -weird.json` treats -weird.json as the input path, not a flag
    var result = convert(SIMPLE_DOC, "--", "-weird.json");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.IO);
    assertThat(result.err()).contains("input file not found: -weird.json");
  }

  @Test
  void blankOutputPathIsAUsageError() {
    var result = convert(SIMPLE_DOC, "--output=");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("--output path must not be empty");
  }
}
