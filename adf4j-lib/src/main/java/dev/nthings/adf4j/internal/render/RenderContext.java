package dev.nthings.adf4j.internal.render;

import java.util.Objects;

import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;

/**
 * Per-render state shared by the whole traversal: the precomputed outline, the conversion's
 * {@link MarkdownOptions}, and the two mutable per-render sinks. The moving cursor lives in
 * {@link RendererState}.
 */
record RenderContext(
    HeadingOutline headingOutline,
    MarkdownOptions options,
    MacroDiagnostics macroDiagnostics,
    UnresolvedTracker unresolvedTracker) {

  static RenderContext from(MarkdownOptions options, HeadingOutline headingOutline) {
    return new RenderContext(
        Objects.requireNonNullElseGet(headingOutline, HeadingOutline::empty),
        Objects.requireNonNull(options, "options"),
        new MacroDiagnostics(),
        new UnresolvedTracker());
  }
}
