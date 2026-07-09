package dev.nthings.adf4j.cli;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.validator.OptionValidatorException;

/// Entry point. The aesh runtime parses argv against the {@link Adf4jCommand} group and runs the
/// matching subcommand; parse failures print the message plus generated help and exit 2.
public final class Main {

  public static void main(String[] args) {
    var exitCode = run(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static int run(String[] args) {
    try {
      return AeshRuntimeRunner.builder()
          .command(Adf4jCommand.class)
          .args(args)
          .execute()
          .getExitCode();
    } catch (RuntimeException exception) {
      // The aesh runtime rethrows populate-time failures (an allowedValues violation among them)
      // wrapped in a bare RuntimeException instead of surfacing them as usage errors.
      if (exception.getCause() instanceof OptionValidatorException cause) {
        System.err.println("error: " + cause.getMessage());
        return ExitCodes.USAGE;
      }
      System.err.println("internal error: " + exception);
      return ExitCodes.INTERNAL;
    }
  }

  private Main() {}
}
