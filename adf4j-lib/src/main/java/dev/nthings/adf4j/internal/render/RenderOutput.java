package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.internal.analyze.DocumentAnalysis;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.List;

/// Result of a render pass: the Markdown body plus any diagnostics raised and resolver lookups
/// declined while rendering. Mirrors the analyze phase's {@link DocumentAnalysis}.
public record RenderOutput(
    String body,
    List<Diagnostic> diagnostics,
    List<UnresolvedResource> unresolved,
    List<ConversionEffect> effects) {

  public RenderOutput {
    diagnostics = List.copyOf(diagnostics);
  }
}
