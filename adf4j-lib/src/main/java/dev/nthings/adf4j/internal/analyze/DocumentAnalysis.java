package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.result.Diagnostic;
import java.util.List;

/// Analysis output: document facts plus Markdown lossiness diagnostics.
public record DocumentAnalysis(DocumentFacts facts, List<Diagnostic> diagnostics) {

  public DocumentAnalysis {
    diagnostics = List.copyOf(diagnostics);
  }
}
