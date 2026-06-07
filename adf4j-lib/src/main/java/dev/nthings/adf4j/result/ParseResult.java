package dev.nthings.adf4j.result;

import java.util.List;

import dev.nthings.adf4j.ast.AdfDocument;

/**
 * The outcome of parsing ADF JSON: the parsed {@code document} (null when the input was blank or not
 * a valid ADF root), the {@code issues} raised (never null), and {@code validAdfRoot} — true when the
 * root parsed as a usable ADF document, including the best-effort case where the only issue is an
 * {@code UNSUPPORTED_VERSION} warning.
 */
public record ParseResult(AdfDocument document, List<ParseIssue> issues, boolean validAdfRoot) {

  public ParseResult {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  public static ParseResult empty() {
    return new ParseResult(null, List.of(), false);
  }
}
