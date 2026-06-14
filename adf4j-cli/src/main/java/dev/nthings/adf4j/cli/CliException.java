package dev.nthings.adf4j.cli;

/** A handled CLI failure carrying the {@link ExitCodes exit code} to return. */
final class CliException extends RuntimeException {

  private final int exitCode;

  CliException(int exitCode, String message) {
    super(message);
    this.exitCode = exitCode;
  }

  CliException(int exitCode, String message, Throwable cause) {
    super(message, cause);
    this.exitCode = exitCode;
  }

  int exitCode() {
    return exitCode;
  }

  static CliException usage(String message) {
    return new CliException(ExitCodes.USAGE, message);
  }

  static CliException io(String message, Throwable cause) {
    return new CliException(ExitCodes.IO, message, cause);
  }
}
