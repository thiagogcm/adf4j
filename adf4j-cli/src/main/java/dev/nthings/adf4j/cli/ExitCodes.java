package dev.nthings.adf4j.cli;

/// The CLI's exit-code contract; codes are stable and scriptable. Usage errors follow the aesh
/// runtime convention (2), so the CLI's own usage failures and the parser's agree.
final class ExitCodes {

  /// Normal completion.
  static final int OK = 0;

  /// Input path or map file not found/unreadable, or an output write failure.
  static final int IO = 1;

  /// Unknown flag/subcommand, an invalid option value, an unknown `{placeholder}` token, or a
  /// malformed/invalid map file. Matches the aesh runtime's own parse-error exit code.
  static final int USAGE = 2;

  /// `validate`: not a valid ADF root or an ERROR diagnostic; `convert --unknown-nodes fail`
  /// aborted.
  static final int CONTENT_FAILURE = 3;

  /// A quality gate tripped: `convert --fail-on-lossy` on a lossy result, or `validate
  /// --fail-on-warning`.
  static final int QUALITY_GATE = 4;

  /// An unexpected internal failure (a bug). Kept distinct from {@link #USAGE} so a crash isn't
  /// read as user error.
  static final int INTERNAL = 70;

  private ExitCodes() {}
}
