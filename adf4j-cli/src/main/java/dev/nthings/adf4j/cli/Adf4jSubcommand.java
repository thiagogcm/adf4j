package dev.nthings.adf4j.cli;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ParseResult;
import java.nio.file.Path;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.jspecify.annotations.Nullable;
import org.slf4j.simple.SimpleLogger;

/// Base of every subcommand: shared options, the input argument, and the exit-code contract.
/// A {@link CliException} is reported as `error:` on stderr; any other exception is a bug,
/// reported as `internal error:`. Stdout is reserved for the deliverable. The aesh annotation
/// processor finds the fields here through superclass walks.
abstract class Adf4jSubcommand implements Command<CommandInvocation> {

  @Mixin CommonOptions common = new CommonOptions();

  @Argument(description = "ADF JSON input file; stdin when omitted or '-'")
  @Nullable String file;

  /// The command body; returns an {@link ExitCodes exit code} or throws {@link CliException}.
  abstract int run();

  @Override
  public final CommandResult execute(CommandInvocation invocation) {
    configureLogging();
    try {
      return CommandResult.valueOf(run());
    } catch (CliException exception) {
      System.err.println("error: " + exception.getMessage());
      if (common.verbose && exception.getCause() != null) {
        exception.getCause().printStackTrace(System.err);
      }
      return CommandResult.valueOf(exception.exitCode());
    } catch (RuntimeException exception) {
      System.err.println("internal error: " + exception);
      if (common.verbose) {
        exception.printStackTrace(System.err);
      }
      return CommandResult.valueOf(ExitCodes.INTERNAL);
    }
  }

  // slf4j-simple prints library warnings to stderr regardless of --quiet. It reads this property
  // once, at the first logger's creation, so set it before the library is touched (also in the
  // native image, where loggers initialize at run time).
  private void configureLogging() {
    System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, common.verbose ? "debug" : "off");
  }

  /// Parses the input. Invalid content is not thrown: the command still writes stdout, then exits
  /// {@link ExitCodes#CONTENT_FAILURE}.
  ParseResult parseInput() {
    return AdfJson.parse(readInput());
  }

  String readInput() {
    var path = file == null || file.equals("-") ? null : Path.of(file);
    return CliIo.read(path, "input file");
  }

  /// Lists diagnostics on stderr, unless `--quiet`.
  void printDiagnostics(List<Diagnostic> diagnostics) {
    if (!common.quiet) {
      diagnostics.forEach(diagnostic -> System.err.println(describe(diagnostic)));
    }
  }

  static String describe(Diagnostic diagnostic) {
    return "[%s] %s: %s".formatted(diagnostic.severity(), diagnostic.code(), diagnostic.message());
  }
}
