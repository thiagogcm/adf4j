package dev.nthings.adf4j.internal.render;

import java.util.List;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedReferences;

/**
 * Result of a render pass: the Markdown body plus any diagnostics raised and resolver lookups declined
 * while rendering. Mirrors the analyze phase's
 * {@link dev.nthings.adf4j.internal.analyze.DocumentAnalysis}.
 */
public record RenderOutput(String body, List<Diagnostic> diagnostics, UnresolvedReferences unresolved) {

  public RenderOutput {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    unresolved = unresolved == null ? UnresolvedReferences.empty() : unresolved;
  }
}
