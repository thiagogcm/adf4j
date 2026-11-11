package dev.nthings.adf4j.options;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// Java callbacks. URL null/blank declines; empty excerpt or page-tree answers suppress output.
public record ResolverHooks(
    @Nullable MediaResolver mediaResolver,
    @Nullable AttachmentResolver attachmentResolver,
    @Nullable FileResolver fileResolver,
    @Nullable PageLinkResolver pageLinkResolver,
    @Nullable PageTreeResolver pageTreeResolver,
    @Nullable ExcerptResolver excerptResolver,
    List<ExtensionRenderer> extensionRenderers) {
  public ResolverHooks {
    extensionRenderers = List.copyOf(extensionRenderers);
  }

  public static ResolverHooks defaults() {
    return new ResolverHooks(null, null, null, null, null, null, List.of());
  }

  public ResolverHooks withMediaResolver(@Nullable MediaResolver value) {
    return new ResolverHooks(
        value,
        attachmentResolver,
        fileResolver,
        pageLinkResolver,
        pageTreeResolver,
        excerptResolver,
        extensionRenderers);
  }

  public ResolverHooks withAttachmentResolver(@Nullable AttachmentResolver value) {
    return new ResolverHooks(
        mediaResolver,
        value,
        fileResolver,
        pageLinkResolver,
        pageTreeResolver,
        excerptResolver,
        extensionRenderers);
  }

  public ResolverHooks withFileResolver(@Nullable FileResolver value) {
    return new ResolverHooks(
        mediaResolver,
        attachmentResolver,
        value,
        pageLinkResolver,
        pageTreeResolver,
        excerptResolver,
        extensionRenderers);
  }

  public ResolverHooks withPageLinkResolver(@Nullable PageLinkResolver value) {
    return new ResolverHooks(
        mediaResolver,
        attachmentResolver,
        fileResolver,
        value,
        pageTreeResolver,
        excerptResolver,
        extensionRenderers);
  }

  public ResolverHooks withPageTreeResolver(@Nullable PageTreeResolver value) {
    return new ResolverHooks(
        mediaResolver,
        attachmentResolver,
        fileResolver,
        pageLinkResolver,
        value,
        excerptResolver,
        extensionRenderers);
  }

  public ResolverHooks withExcerptResolver(@Nullable ExcerptResolver value) {
    return new ResolverHooks(
        mediaResolver,
        attachmentResolver,
        fileResolver,
        pageLinkResolver,
        pageTreeResolver,
        value,
        extensionRenderers);
  }

  public ResolverHooks withExtensionRenderers(List<ExtensionRenderer> value) {
    return new ResolverHooks(
        mediaResolver,
        attachmentResolver,
        fileResolver,
        pageLinkResolver,
        pageTreeResolver,
        excerptResolver,
        value);
  }
}
