package dev.nthings.adf4j.result;

import java.util.List;

/// Conformance to the bundled ADF schema, not acceptance by a particular Atlassian product.
public record ValidationResult(List<Diagnostic> diagnostics) implements OperationResult {
  public ValidationResult {
    diagnostics = List.copyOf(diagnostics);
  }
}
