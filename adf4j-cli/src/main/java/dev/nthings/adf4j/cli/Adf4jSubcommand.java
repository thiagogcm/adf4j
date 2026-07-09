package dev.nthings.adf4j.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.jspecify.annotations.Nullable;

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
}
