package dev.nthings.adf4j.internal.render;

import java.util.List;

import dev.nthings.adf4j.result.ParseIssue;

/**
 * Result of a render pass: the Markdown body plus any diagnostics raised while rendering. Mirrors the
 * analyze phase's {@link dev.nthings.adf4j.internal.analyze.DocumentAnalysis}.
 */
public record RenderOutput(String body, List<ParseIssue> diagnostics) {

  public RenderOutput {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }
}
