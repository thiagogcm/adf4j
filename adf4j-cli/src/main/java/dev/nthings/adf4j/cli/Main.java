package dev.nthings.adf4j.cli;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Entry point. Delegates to {@link Cli}; the package-visible {@code run} overload lets tests drive the
 * CLI with explicit streams and assert the exit code without touching the JVM's.
 */
public final class Main {

  public static void main(String[] args) {
    var exitCode = run(args, System.in, System.out, System.err);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
    return new Cli(in, out, err).run(args);
  }

  private Main() {}
}
