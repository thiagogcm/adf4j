package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.List;

/// Result of the single analysis pass: the heading outline (for the renderer), content metadata,
/// and any lossiness diagnostics raised while scanning the document.
public record DocumentAnalysis(
    HeadingOutline outline, ContentMetadata metadata, List<Diagnostic> diagnostics) {

  public DocumentAnalysis {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  public static DocumentAnalysis empty() {
    return new DocumentAnalysis(HeadingOutline.empty(), ContentMetadata.empty(), List.of());
  }
}
