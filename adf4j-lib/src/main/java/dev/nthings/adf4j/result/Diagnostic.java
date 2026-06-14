package dev.nthings.adf4j.result;

import org.jspecify.annotations.Nullable;

/**
 * One diagnostic raised while converting a document. {@code severity} categorizes it so consumers can
 * react programmatically (see {@link MarkdownResult#wasLossy()}): {@link Severity#ERROR} aborts the
 * conversion or yields an empty body, {@link Severity#WARNING} marks content that converted but was
 * dropped or altered, and {@link Severity#INFO} is a non-lossy note.
 */
public record Diagnostic(String code, String message, @Nullable Throwable cause, Severity severity) {

  /** How serious a {@link Diagnostic} is, from a non-lossy note up to a fatal error. */
  public enum Severity {
    INFO,
    WARNING,
    ERROR
  }

  public Diagnostic {
    severity = severity == null ? Severity.ERROR : severity;
  }

  /** A diagnostic that defaults to {@link Severity#ERROR}. */
  public Diagnostic(String code, String message, @Nullable Throwable cause) {
    this(code, message, cause, Severity.ERROR);
  }
}
