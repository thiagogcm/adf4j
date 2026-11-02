package dev.nthings.adf4j.result;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// One diagnostic raised while converting a document. `code` is a stable machine-readable
/// identifier that fixes the {@link #severity()}, so consumers can react programmatically (see
/// {@link MarkdownResult#wasLossy()}). `cause` is the originating exception when one exists, else
/// `null`.
public record Diagnostic(Code code, String message, @Nullable Throwable cause) {

  /// How serious a {@link Diagnostic} is. {@link #ERROR} aborts the conversion or yields an empty
  /// body, {@link #WARNING} marks content that converted but was dropped or altered, and
  /// {@link #INFO} is a non-lossy note.
  public enum Severity {
    INFO,
    WARNING,
    ERROR
  }

  /// What a {@link Diagnostic} reports. Every code has one fixed {@link Severity}.
  public enum Code {
    /// The payload is not valid JSON, is nested too deeply, or holds a number too large to read.
    INVALID_JSON(Severity.ERROR),
    /// The parsed document is `null`.
    /// The root is not a JSON object.
    INVALID_ROOT_TYPE(Severity.ERROR),
    /// The root `type` is not `doc`.
    INVALID_ROOT_NODE(Severity.ERROR),
    /// The root has no integer `version`.
    INVALID_VERSION(Severity.ERROR),
    /// The root `content` is not an array.
    INVALID_CONTENT(Severity.ERROR),
    /// The root `version` is not `1`; parsing continues best effort.
    UNSUPPORTED_VERSION(Severity.WARNING),
    /// Malformed JSON has no place in the AST and would be lost on write.
    UNPRESERVED_JSON(Severity.WARNING),
    /// Unknown nodes rendered as placeholders.
    UNKNOWN_NODE_PLACEHOLDER(Severity.WARNING),
    /// Unknown nodes dropped from the output.
    UNKNOWN_NODE_SKIPPED(Severity.WARNING),
    /// Unknown nodes preserved as raw JSON.
    UNKNOWN_NODE_PRESERVED(Severity.INFO),
    /// Unknown marks dropped from the output.
    UNKNOWN_MARK_DROPPED(Severity.WARNING),
    /// Unsupported macros rendered as placeholders.
    UNSUPPORTED_MACRO(Severity.WARNING);

    private final Severity severity;

    Code(Severity severity) {
      this.severity = severity;
    }

    /// The severity of every diagnostic with this code.
    public Severity severity() {
      return severity;
    }
  }

  public Diagnostic {
    Objects.requireNonNull(code, "code");
  }

  /// A diagnostic with no originating exception.
  public Diagnostic(Code code, String message) {
    this(code, message, null);
  }

  /// The severity fixed by {@link #code()}.
  public Severity severity() {
    return code.severity();
  }
}
