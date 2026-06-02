package dev.nthings.adf4j.options;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;

/**
 * Immutable configuration for an ADF-to-Markdown conversion. {@code imageSizeAttributes} emits the
 * non-GFM {@code {width= height=}} image suffix (off by default; the library targets GFM).
 */
public record MarkdownOptions(
    UnknownNodePolicy unknownNodePolicy,
    ConfluenceRenderContext context,
    boolean imageSizeAttributes) {

  public MarkdownOptions {
    unknownNodePolicy = unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : unknownNodePolicy;
    context = context == null ? ConfluenceRenderContext.empty() : context;
  }

  public static MarkdownOptions defaults() {
    return new MarkdownOptions(UnknownNodePolicy.PLACEHOLDER, ConfluenceRenderContext.empty(), false);
  }

  public MarkdownOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    return new MarkdownOptions(policy, context, imageSizeAttributes);
  }

  public MarkdownOptions withContext(ConfluenceRenderContext renderContext) {
    return new MarkdownOptions(unknownNodePolicy, renderContext, imageSizeAttributes);
  }

  public MarkdownOptions withImageSizeAttributes(boolean enabled) {
    return new MarkdownOptions(unknownNodePolicy, context, enabled);
  }
}
