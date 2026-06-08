package dev.nthings.adf4j.internal.render;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.nthings.adf4j.extension.ExtensionRenderer;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.options.AttachmentResolver;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.MediaResolver;
import dev.nthings.adf4j.options.PageLinkResolver;
import dev.nthings.adf4j.options.PageTreeResolver;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;

/**
 * Per-render configuration derived once from {@link MarkdownOptions}; the moving cursor lives in
 * {@link RendererState}. Mostly immutable, save the per-render {@link #macroDiagnostics()} sink.
 */
record RenderContext(
    HeadingOutline headingOutline,
    Map<String, AttachmentReference> attachmentReferencesByTitle,
    UnknownNodePolicy unknownNodePolicy,
    boolean imageSizeAttributes,
    TableFallback tableFallback,
    MediaResolver mediaResolver,
    boolean htmlVisualMarks,
    List<ExtensionRenderer> extensionRenderers,
    AttachmentResolver attachmentResolver,
    PageLinkResolver pageLinkResolver,
    PageTreeResolver pageTreeResolver,
    boolean collapseHardBreaks,
    boolean escapeParentheses,
    MacroDiagnostics macroDiagnostics) {

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
        requiredOptions.htmlVisualMarks(),
        requiredOptions.extensionRenderers(),
        requiredOptions.attachmentResolver(),
        requiredOptions.pageLinkResolver(),
        requiredOptions.pageTreeResolver(),
        requiredOptions.collapseHardBreaks(),
        requiredOptions.escapeParentheses(),
        new MacroDiagnostics());
  }
}
