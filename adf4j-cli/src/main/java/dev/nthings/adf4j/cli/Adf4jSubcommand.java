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

/// Base of every subcommand: the shared options, the input argument, and the exit-code contract.
/// {@link #execute} maps {@link #run}'s outcome onto it: a {@link CliException} is a handled
/// failure reported as `error:` on stderr, anything else is a bug reported as `internal error:`.
/// Stdout stays reserved for the deliverable. Both the aesh runtime and the annotation processor
/// pick up the fields declared here through their superclass walks.
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

  // The library logs through slf4j-simple, which would otherwise print warnings and their stack
  // traces to stderr whatever --quiet says. It reads this property once, when the first logger is
  // created, so this must run before anything touches the library; that also holds in the native
  // image, where the logger is initialized at run time.
  private void configureLogging() {
    System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, common.verbose ? "debug" : "off");
  }

  /// Reads and parses the input. Bad content is not an exception: the result is invalid and the
  /// command still writes its stdout, then exits {@link ExitCodes#CONTENT_FAILURE}.
  ParseResult parseInput() {
    var path = file == null || file.equals("-") ? null : Path.of(file);
    var parsed = AdfJson.parse(CliIo.read(path, "input file"));
    // Blank input is invalid without a diagnostic to show for it.
    if (!parsed.isValid() && parsed.diagnostics().isEmpty() && !common.quiet) {
      System.err.println("error: input is blank");
    }
    return parsed;
  }

  /// Lists diagnostics on stderr, unless `--quiet`.
  void printDiagnostics(List<Diagnostic> diagnostics) {
    if (!common.quiet) {
      diagnostics.forEach(diagnostic -> System.err.println(describe(diagnostic)));
    }
  }

  /// One diagnostic as a line of text.
  static String describe(Diagnostic diagnostic) {
    return "[%s] %s: %s".formatted(diagnostic.severity(), diagnostic.code(), diagnostic.message());
  }
}
