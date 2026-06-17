package dev.nthings.adf4j.result;

import dev.nthings.adf4j.ast.AdfDocument;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// The outcome of parsing ADF JSON: the parsed `document` (null when the input was blank or not
/// a valid ADF root), the `issues` raised (never null), and `validAdfRoot`, true when the
/// root parsed as a usable ADF document, including the best-effort case where the only issue is an
/// `UNSUPPORTED_VERSION` warning.
public record ParseResult(
    @Nullable AdfDocument document, List<Diagnostic> issues, boolean validAdfRoot) {

  public ParseResult {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  public static ParseResult empty() {
    return new ParseResult(null, List.of(), false);
  }
}
