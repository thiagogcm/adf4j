package dev.nthings.adf4j.cli;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.validator.OptionValidatorException;

/// Entry point: runs the {@link Adf4jCommand} group through aesh. Parse failures exit 2.
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
      // aesh wraps populate-time failures (such as allowedValues) in a bare RuntimeException.
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
