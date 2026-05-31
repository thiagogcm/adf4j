package dev.nthings.adf4j.renderer;

import java.util.Map;
import java.util.Objects;

import dev.nthings.adf4j.AttachmentReference;
import dev.nthings.adf4j.PageTitleResolver;
import dev.nthings.adf4j.RenderOptions;
import dev.nthings.adf4j.model.MacroContext;
import dev.nthings.adf4j.model.PageLinkResolver;
import dev.nthings.adf4j.model.UnknownNodePolicy;

/**
 * Immutable, per-render configuration derived once from {@link RenderOptions}. Stays constant for
 * the whole traversal; the moving cursor lives in {@link RendererState}.
 */
record RenderContext(
    String pageTitle,
    String currentPageId,
    MacroContext macroContext,
    HeadingOutline headingOutline,
    Map<String, AttachmentReference> attachmentReferencesByTitle,
    PageLinkResolver linkResolver,
    PageTitleResolver pageTitleResolver,
    UnknownNodePolicy unknownNodePolicy) {

  static RenderContext from(RenderOptions options, HeadingOutline headingOutline) {
    var requiredOptions = Objects.requireNonNull(options, "options");
    var safeOutline = Objects.requireNonNullElseGet(headingOutline, HeadingOutline::empty);
    var confluence = requiredOptions.context();
    return new RenderContext(
        confluence.pageTitle(),
        confluence.currentPageId(),
        MacroContext.from(confluence.excerpts(), safeOutline.headings()),
        safeOutline,
        confluence.attachmentReferencesByTitle(),
        confluence.pageLinkResolver(),
        confluence.pageTitleResolver(),
        requiredOptions.unknownNodePolicy());
  }
}
