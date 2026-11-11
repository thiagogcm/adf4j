package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.List;

/// Output of the analysis pass: heading outline, content metadata and lossiness diagnostics.
public record DocumentAnalysis(
    HeadingOutline outline, ContentMetadata metadata, List<Diagnostic> diagnostics) {

  public DocumentAnalysis {
    diagnostics = List.copyOf(diagnostics);
  }

  public static DocumentAnalysis empty() {
    return new DocumentAnalysis(HeadingOutline.empty(), ContentMetadata.empty(), List.of());
  }
}
