package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfDocument;

/// Renderer backend plugged into {@link dev.nthings.adf4j.internal.engine.AdfPipeline}. Returns
/// only the body: diagnostics, unresolved lookups, and effects go to the environment's
/// {@link RenderEvents}, which the pipeline reads itself. Stateless: all per-call state arrives in
/// {@link RenderEnvironment}.
@FunctionalInterface
public interface AdfDocumentRenderer {
  String render(AdfDocument document, RenderEnvironment environment);
}
