package dev.nthings.adf4j.result;

import java.util.List;

/// Raised by the explicitly throwing conveniences when an operation failed.
public final class AdfException extends IllegalArgumentException {
  private final List<Diagnostic> diagnostics;

  public AdfException(List<Diagnostic> diagnostics) {
    super(firstErrorMessage(diagnostics));
    this.diagnostics = List.copyOf(diagnostics);
  }

  public List<Diagnostic> diagnostics() {
    return diagnostics;
  }

  static String firstErrorMessage(List<Diagnostic> diagnostics) {
    return diagnostics.stream()
        .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
        .map(Diagnostic::message)
        .findFirst()
        .orElse("ADF operation failed");
  }
}
