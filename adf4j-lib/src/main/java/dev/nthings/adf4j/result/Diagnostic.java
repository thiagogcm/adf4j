package dev.nthings.adf4j.result;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// A machine-readable issue. Paths are JSON Pointers; details carry structured context.
/// The cause is available to Java callers and omitted from portable JSON output.
public record Diagnostic(
    Code code,
    String message,
    @Nullable Throwable cause,
    List<String> paths,
    Map<String, String> details) {

  /// ERROR means the operation failed; WARNING reports an issue in a completed operation.
  /// INFO provides context without marking a problem.
  public enum Severity {
    INFO,
    WARNING,
    ERROR
  }

  /// What a {@link Diagnostic} reports. Every code has one fixed {@link Severity}.
  public enum Code {
    /// The payload is not valid JSON, is nested too deeply, or holds a number too large to read.
    INVALID_JSON(Severity.ERROR),
    EMPTY_INPUT(Severity.ERROR),
    INVALID_OPTIONS(Severity.ERROR),
    SCHEMA_VIOLATION(Severity.ERROR),
    UNKNOWN_NODE_REJECTED(Severity.ERROR),
    CALLBACK_FAILED(Severity.WARNING),
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
    paths = List.copyOf(paths);
    details = Map.copyOf(details);
  }

  public Diagnostic(Code code, String message, @Nullable Throwable cause) {
    this(code, message, cause, List.of(), Map.of());
  }

  /// This diagnostic located at one JSON Pointer.
  public Diagnostic at(String path) {
    return at(List.of(path));
  }

  /// This diagnostic located at every JSON Pointer in `paths`, one per occurrence.
  public Diagnostic at(List<String> paths) {
    return new Diagnostic(code, message, cause, paths, details);
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
