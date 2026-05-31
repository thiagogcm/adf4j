package dev.nthings.adf4j;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;

public record RenderOptions(
    UnknownNodePolicy unknownNodePolicy,
    ConfluenceRenderContext context) {

  public RenderOptions {
    unknownNodePolicy = unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : unknownNodePolicy;
    context = context == null ? ConfluenceRenderContext.empty() : context;
  }

  public static RenderOptions defaults() {
    return new RenderOptions(UnknownNodePolicy.PLACEHOLDER, ConfluenceRenderContext.empty());
  }

  public RenderOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    return new RenderOptions(policy, context);
  }

  public RenderOptions withContext(ConfluenceRenderContext renderContext) {
    return new RenderOptions(unknownNodePolicy, renderContext);
  }
}
