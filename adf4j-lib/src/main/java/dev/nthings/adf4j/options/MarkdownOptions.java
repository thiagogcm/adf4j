package dev.nthings.adf4j.options;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;

/**
 * Immutable configuration for an ADF-to-Markdown conversion. {@code imageSizeAttributes} emits the
 * non-GFM {@code {width= height=}} image suffix (off by default; the library targets GFM).
 * {@code tableFallback} selects how a GFM-safe table without an all-header first row is rendered
 * (raw HTML by default). {@code mediaResolver} is an optional hook that turns file media (which
 * carries ids, not a URL) into a concrete link; {@code null} keeps the {@code media:} placeholder.
 */
public record MarkdownOptions(
    UnknownNodePolicy unknownNodePolicy,
    ConfluenceRenderContext context,
    boolean imageSizeAttributes,
    TableFallback tableFallback,
    MediaResolver mediaResolver) {

  public MarkdownOptions {
    unknownNodePolicy = unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : unknownNodePolicy;
    context = context == null ? ConfluenceRenderContext.empty() : context;
    tableFallback = tableFallback == null ? TableFallback.HTML : tableFallback;
    // mediaResolver intentionally nullable: null means "use the default media: placeholder".
  }

  public static MarkdownOptions defaults() {
    return new MarkdownOptions(
        UnknownNodePolicy.PLACEHOLDER, ConfluenceRenderContext.empty(), false, TableFallback.HTML, null);
  }

  public MarkdownOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    return new MarkdownOptions(policy, context, imageSizeAttributes, tableFallback, mediaResolver);
  }

  public MarkdownOptions withContext(ConfluenceRenderContext renderContext) {
    return new MarkdownOptions(unknownNodePolicy, renderContext, imageSizeAttributes, tableFallback, mediaResolver);
  }

  public MarkdownOptions withImageSizeAttributes(boolean enabled) {
    return new MarkdownOptions(unknownNodePolicy, context, enabled, tableFallback, mediaResolver);
  }

  public MarkdownOptions withTableFallback(TableFallback fallback) {
    return new MarkdownOptions(unknownNodePolicy, context, imageSizeAttributes, fallback, mediaResolver);
  }

  public MarkdownOptions withMediaResolver(MediaResolver resolver) {
    return new MarkdownOptions(unknownNodePolicy, context, imageSizeAttributes, tableFallback, resolver);
  }
}
