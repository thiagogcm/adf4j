package dev.nthings.adf4j.internal.render;

import java.util.Map;
import java.util.Objects;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.MediaResolver;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;

/**
 * Immutable, per-render configuration derived once from {@link MarkdownOptions}. Stays constant for
 * the whole traversal; the moving cursor lives in {@link RendererState}.
 */
record RenderContext(
    HeadingOutline headingOutline,
    Map<String, AttachmentReference> attachmentReferencesByTitle,
    UnknownNodePolicy unknownNodePolicy,
    boolean imageSizeAttributes,
    TableFallback tableFallback,
    MediaResolver mediaResolver,
    boolean htmlVisualMarks) {

  static RenderContext from(MarkdownOptions options, HeadingOutline headingOutline) {
    var requiredOptions = Objects.requireNonNull(options, "options");
    var safeOutline = Objects.requireNonNullElseGet(headingOutline, HeadingOutline::empty);
    var confluence = requiredOptions.context();
    return new RenderContext(
        safeOutline,
        confluence.attachmentReferencesByTitle(),
        requiredOptions.unknownNodePolicy(),
        requiredOptions.imageSizeAttributes(),
        requiredOptions.tableFallback(),
        requiredOptions.mediaResolver(),
        requiredOptions.htmlVisualMarks());
  }
}
