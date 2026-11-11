package dev.nthings.adf4j.result;

import dev.nthings.adf4j.ast.AdfDocument;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Parsed ADF JSON. `document` is null when the input is blank or not a valid ADF root. Warnings
/// can accompany a document: UNSUPPORTED_VERSION (parsed best effort) or UNPRESERVED_JSON (lost on
/// write).
public record ParseResult(@Nullable AdfDocument document, List<Diagnostic> diagnostics)
    implements OperationResult {

  public ParseResult {
    diagnostics = List.copyOf(diagnostics);
  }

  public AdfDocument requireDocument() {
    requireSuccess();
    return Objects.requireNonNull(document);
  }

  /// Whether a document was produced; warnings do not count.
  public boolean isSuccess() {
    return document != null;
  }
}
