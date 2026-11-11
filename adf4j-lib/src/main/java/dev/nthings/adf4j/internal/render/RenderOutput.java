package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.List;

/// Markdown output and reports for the content actually rendered.
public record RenderOutput(
    String body,
    List<Diagnostic> diagnostics,
    List<UnresolvedResource> unresolved,
    List<ConversionEffect> effects) {

  public RenderOutput {
    diagnostics = List.copyOf(diagnostics);
  }
}
