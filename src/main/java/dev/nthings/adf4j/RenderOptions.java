package dev.nthings.adf4j;

import dev.nthings.adf4j.model.UnknownNodePolicy;

public record RenderOptions(
    UnknownNodePolicy unknownNodePolicy,
    AdfRenderContext context) {

  public RenderOptions {
    unknownNodePolicy = unknownNodePolicy == null ? UnknownNodePolicy.PLACEHOLDER : unknownNodePolicy;
    context = context == null ? AdfRenderContext.none() : context;
  }

  public static RenderOptions defaults() {
    return new RenderOptions(UnknownNodePolicy.PLACEHOLDER, AdfRenderContext.none());
  }

  public RenderOptions withUnknownNodePolicy(UnknownNodePolicy policy) {
    return new RenderOptions(policy, context);
  }

  public RenderOptions withContext(AdfRenderContext renderContext) {
    return new RenderOptions(unknownNodePolicy, renderContext);
  }
}
