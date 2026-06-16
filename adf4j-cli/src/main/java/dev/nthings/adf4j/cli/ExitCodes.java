package dev.nthings.adf4j.cli;

/** The CLI's exit-code contract; codes are stable and scriptable. */
final class ExitCodes {

  /** Normal completion. */
  static final int OK = 0;

  /** Unknown flag/subcommand, an unknown {@code {placeholder}} token, or a malformed/invalid map file. */
  static final int USAGE = 1;

  /** Input path or map file not found/unreadable, or an output write failure. */
  static final int IO = 2;

  /** {@code validate}: not a valid ADF root or an ERROR diagnostic; {@code convert --unknown-nodes fail} aborted. */
  static final int CONTENT_FAILURE = 3;

  /** A quality gate tripped: {@code convert --fail-on-lossy} on a lossy result, or {@code validate --fail-on-warning}. */
  static final int QUALITY_GATE = 4;

  /** An unexpected internal failure (a bug). Kept distinct from {@link #USAGE} so a crash isn't read as user error. */
  static final int INTERNAL = 70;

  private ExitCodes() {}
}
