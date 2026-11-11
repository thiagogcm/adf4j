package dev.nthings.adf4j.cli;

/// Exit-code contract: codes are stable and scriptable. Usage (2) matches the aesh parser.
final class ExitCodes {

  /// Normal completion.
  static final int OK = 0;

  /// Input path or map file not found/unreadable, or an output write failure.
  static final int IO = 1;

  /// Unknown flag or subcommand, invalid option value, unknown `{placeholder}`, or a malformed
  /// map file.
  static final int USAGE = 2;

  /// The input has no valid ADF root, whatever the subcommand (stdout is still written), or
  /// `convert --unknown-nodes fail` aborted.
  static final int CONTENT_FAILURE = 3;

  /// A `--fail-on-*` gate tripped.
  static final int QUALITY_GATE = 4;

  /// A bug, kept distinct from {@link #USAGE} so a crash isn't read as user error.
  static final int INTERNAL = 70;

  private ExitCodes() {}
}
