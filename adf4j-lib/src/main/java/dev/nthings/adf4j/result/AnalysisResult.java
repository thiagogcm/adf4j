package dev.nthings.adf4j.result;

import dev.nthings.adf4j.metadata.ContentMetadata;
import java.util.List;

/// Source metadata and diagnostics. Custom renderer output is not analyzed.
public record AnalysisResult(ContentMetadata metadata, List<Diagnostic> diagnostics)
    implements OperationResult {
  public AnalysisResult {
    diagnostics = List.copyOf(diagnostics);
  }
}
