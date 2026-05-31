package dev.nthings.adf4j.options;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;

/** Immutable configuration for an ADF-to-Markdown conversion. */
public record MarkdownOptions(
    UnknownNodePolicy unknownNodePolicy,
    ConfluenceRenderContext context) {

  public MarkdownOptions {
    unknownNodePolicy = unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : unknownNodePolicy;
    context = context == null ? ConfluenceRenderContext.empty() : context;
  }

  public static MarkdownOptions defaults() {
    return new MarkdownOptions(UnknownNodePolicy.PLACEHOLDER, ConfluenceRenderContext.empty());
  }

  public MarkdownOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    return new MarkdownOptions(policy, context);
  }

  public MarkdownOptions withContext(ConfluenceRenderContext renderContext) {
    return new MarkdownOptions(unknownNodePolicy, renderContext);
  }
}
