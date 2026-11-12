package dev.nthings.adf4j.result;

import dev.nthings.adf4j.metadata.ContentMetadata;
import java.util.List;

/// Source metadata and diagnostics. Custom renderer output is not analyzed.
public record AnalysisResult(ContentMetadata metadata, List<Diagnostic> diagnostics)
    implements OperationResult {
  public AnalysisResult {
    diagnostics = List.copyOf(diagnostics);
  }

  /// A failure before any content was read: empty metadata.
  public static AnalysisResult failed(List<Diagnostic> diagnostics) {
    return new AnalysisResult(ContentMetadata.empty(), diagnostics);
  }
}
