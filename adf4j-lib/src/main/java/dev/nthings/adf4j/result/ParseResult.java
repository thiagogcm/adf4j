package dev.nthings.adf4j.result;

import java.util.List;

import dev.nthings.adf4j.ast.AdfDocument;

public record ParseResult(AdfDocument document, List<ParseIssue> issues, boolean validAdfRoot) {

  public static ParseResult empty() {
    return new ParseResult(null, List.of(), false);
  }
}
