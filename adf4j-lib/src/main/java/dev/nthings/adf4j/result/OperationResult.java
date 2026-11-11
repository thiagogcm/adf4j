package dev.nthings.adf4j.result;

import java.util.List;

/// An operation's outcome. Expected input failures are diagnostics; programming errors still throw.
public interface OperationResult {
  List<Diagnostic> diagnostics();

  default boolean isSuccess() {
    return !hasErrors();
  }

  default boolean hasErrors() {
    return diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
  }

  default boolean hasWarnings() {
    return diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.WARNING);
  }

  default void requireSuccess() {
    if (!isSuccess()) throw new AdfException(diagnostics());
  }
}
