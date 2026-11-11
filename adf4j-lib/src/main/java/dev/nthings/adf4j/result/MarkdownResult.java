package dev.nthings.adf4j.result;

import dev.nthings.adf4j.metadata.ContentMetadata;
import java.util.List;

/// Conversion outcome. A failed result has no publishable body. Metadata describes the source.
public record MarkdownResult(
    String body,
    ContentMetadata metadata,
    List<Diagnostic> diagnostics,
    List<UnresolvedResource> unresolved,
    List<ConversionEffect> effects)
    implements OperationResult {
  public MarkdownResult {
    unresolved = List.copyOf(unresolved);
    diagnostics = List.copyOf(diagnostics);
    effects = List.copyOf(effects);
  }

  public String requireBody() {
    requireSuccess();
    return body;
  }
}
