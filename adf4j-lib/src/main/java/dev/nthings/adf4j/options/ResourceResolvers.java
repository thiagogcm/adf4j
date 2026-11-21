package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/// Renderer-neutral Java callbacks for resolving external resources, page trees, and AST excerpts.
/// Shared across output renderers (such as Markdown and HTML). URL `null`/blank declines; empty
/// page-tree or excerpt content suppresses output.
public record ResourceResolvers(
    @Nullable MediaResolver mediaResolver,
    @Nullable AttachmentResolver attachmentResolver,
    @Nullable FileResolver fileResolver,
    @Nullable PageLinkResolver pageLinkResolver,
    @Nullable PageTreeResolver pageTreeResolver,
    @Nullable ExcerptContentResolver excerptContentResolver) {

  public static ResourceResolvers defaults() {
    return new ResourceResolvers(null, null, null, null, null, null);
  }

  public ResourceResolvers withMediaResolver(@Nullable MediaResolver value) {
    return new ResourceResolvers(
        value,
        attachmentResolver,
        fileResolver,
        pageLinkResolver,
        pageTreeResolver,
        excerptContentResolver);
  }

  public ResourceResolvers withAttachmentResolver(@Nullable AttachmentResolver value) {
    return new ResourceResolvers(
        mediaResolver,
        value,
        fileResolver,
        pageLinkResolver,
        pageTreeResolver,
        excerptContentResolver);
  }

  public ResourceResolvers withFileResolver(@Nullable FileResolver value) {
    return new ResourceResolvers(
        mediaResolver,
        attachmentResolver,
        value,
        pageLinkResolver,
        pageTreeResolver,
        excerptContentResolver);
  }

  public ResourceResolvers withPageLinkResolver(@Nullable PageLinkResolver value) {
    return new ResourceResolvers(
        mediaResolver,
        attachmentResolver,
        fileResolver,
        value,
        pageTreeResolver,
        excerptContentResolver);
  }

  public ResourceResolvers withPageTreeResolver(@Nullable PageTreeResolver value) {
    return new ResourceResolvers(
        mediaResolver,
        attachmentResolver,
        fileResolver,
        pageLinkResolver,
        value,
        excerptContentResolver);
  }

  public ResourceResolvers withExcerptContentResolver(@Nullable ExcerptContentResolver value) {
    return new ResourceResolvers(
        mediaResolver, attachmentResolver, fileResolver, pageLinkResolver, pageTreeResolver, value);
  }
}
