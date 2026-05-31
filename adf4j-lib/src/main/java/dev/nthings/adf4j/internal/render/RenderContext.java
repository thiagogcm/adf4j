package dev.nthings.adf4j.internal.render;

import java.util.Map;
import java.util.Objects;

import dev.nthings.adf4j.AttachmentReference;
import dev.nthings.adf4j.RenderOptions;
import dev.nthings.adf4j.UnknownNodePolicy;

/**
 * Immutable, per-render configuration derived once from {@link RenderOptions}. Stays constant for
 * the whole traversal; the moving cursor lives in {@link RendererState}.
 */
record RenderContext(
    HeadingOutline headingOutline,
    Map<String, AttachmentReference> attachmentReferencesByTitle,
    UnknownNodePolicy unknownNodePolicy) {

  static RenderContext from(RenderOptions options, HeadingOutline headingOutline) {
    var requiredOptions = Objects.requireNonNull(options, "options");
    var safeOutline = Objects.requireNonNullElseGet(headingOutline, HeadingOutline::empty);
    var confluence = requiredOptions.context();
    return new RenderContext(
        safeOutline,
        confluence.attachmentReferencesByTitle(),
        requiredOptions.unknownNodePolicy());
  }
}
