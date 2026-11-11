package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.convert;
import static dev.nthings.adf4j.cli.CliTestSupport.run;
import static dev.nthings.adf4j.cli.CliTestSupport.runNoInput;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GlobalOptionsTest {

  @ParameterizedTest
  @CsvSource({
    "convert,write",
    "convert,flush",
    "analyze,write",
    "analyze,flush",
    "validate,write",
    "validate,flush"
  })
  void stdout_failures_are_io_errors(String command, String failure) {
    var output =
        new OutputStream() {
          @Override
          public void write(int value) throws IOException {
            if (failure.equals("write")) {
              throw new IOException("broken output");
            }
          }

          @Override
          public void flush() throws IOException {
            if (failure.equals("flush")) {
              throw new IOException("broken flush");
            }
          }
        };
    var result = run(SIMPLE_DOC, output, command);
    assertThat(result.exitCode()).isEqualTo(ExitCodes.IO);
    assertThat(result.err()).contains("failed to write stdout").doesNotContain("internal error");
  }

  @Test
  void versionLongAndShortPrintTheVersion() {
    assertThat(Adf4jCommand.VERSION).isNotBlank().isNotEqualTo("unknown");
    assertThat(runNoInput("--version").out()).isEqualTo("adf4j " + Adf4jCommand.VERSION + "\n");
    assertThat(runNoInput("-V").out()).isEqualTo("adf4j " + Adf4jCommand.VERSION + "\n");
  }

  @Test
  void lowercaseVIsVerboseNotVersion() {
    // -v is verbose; -V is the version (flags are case-sensitive).
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
  void invalidInputIsSilentUnderQuietInARealProcess() throws Exception {
    for (var command : new String[] {"convert", "analyze", "validate"}) {
      var result = runProcess("{ not json", command, "-q");
      assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
      assertThat(result.err()).isEmpty();
    }
  }

  @Test
  void libraryLogsStayOffByDefaultAndReappearUnderVerbose() throws Exception {
    var doc = CliTestSupport.UNKNOWN_NODE_DOC; // the renderer logs a WARN for it

    var byDefault = runProcess(doc, "convert");
    assertThat(byDefault.exitCode()).isZero();
    assertThat(byDefault.err()).doesNotContain("WARN").doesNotContain("InlineRenderer");

    var verbose = runProcess(doc, "convert", "-v");
    assertThat(verbose.exitCode()).isZero();
    assertThat(verbose.err()).contains("WARN").contains("bogusBlock");
  }

  // slf4j-simple reads its level once per JVM, so logging needs a fresh process.
  private static CliTestSupport.Result runProcess(String stdin, String... args) throws Exception {
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.addAll(List.of("-cp", System.getProperty("java.class.path")));
    command.add(Main.class.getName());
    command.addAll(List.of(args));
    var process = new ProcessBuilder(command).start();
    try (var input = process.getOutputStream()) {
      input.write(stdin.getBytes(StandardCharsets.UTF_8));
    }
    var err = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
    var out = readAll(process.getInputStream());
    return new CliTestSupport.Result(process.waitFor(), out, err.join());
  }

  private static String readAll(InputStream stream) {
    try {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @Test
  void noArgsPrintsGlobalHelp() {
    var result = runNoInput();
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).contains("convert").contains("analyze").contains("validate");
  }

  @Test
  void helpLongAndShortPrintGlobalUsage() {
    assertThat(runNoInput("--help").out()).contains("adf4j").contains("convert");
    assertThat(runNoInput("-h").out()).contains("convert");
  }

  @Test
  void perCommandHelp() {
    assertThat(runNoInput("convert", "--help").out()).contains("convert").contains("--format");
    assertThat(runNoInput("analyze", "--help").out()).contains("analyze").contains("--select");
    assertThat(runNoInput("validate", "--help").out())
        .contains("validate")
        .contains("--fail-on-warning");
  }

  @Test
  void unknownCommandIsAUsageError() {
    var result = run(SIMPLE_DOC, "frobnicate");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("frobnicate");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void unknownOptionIsAUsageError() {
    var result = convert(SIMPLE_DOC, "--bogus");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("--bogus");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void doubleDashEndsOptionParsingWithinACommand() {
    // After `--`, -weird.json is the input path, not a flag.
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
