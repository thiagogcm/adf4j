package dev.nthings.adf4j.result;

import dev.nthings.adf4j.ast.AdfDocument;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// The outcome of parsing ADF JSON: the parsed `document`, or `null` when the input was blank or
/// not a valid ADF root, and the `diagnostics` raised (never null). A valid result can still carry
/// warnings: `UNSUPPORTED_VERSION` means the document parsed best effort, and `UNPRESERVED_JSON`
/// means part of the JSON is malformed in a way the document cannot hold, so writing it back would
/// lose that part.
public record ParseResult(@Nullable AdfDocument document, List<Diagnostic> diagnostics) {

  public ParseResult {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  /// Whether parsing produced a document.
  public boolean isValid() {
    return document != null;
  }
}
