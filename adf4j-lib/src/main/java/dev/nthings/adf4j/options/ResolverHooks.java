package dev.nthings.adf4j.options;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Java callbacks for Markdown conversion: renderer-neutral {@link ResourceResolvers} plus
/// Markdown-valued excerpt and extension hooks.
public record ResolverHooks(
    ResourceResolvers resources,
    @Nullable ExcerptResolver excerptResolver,
    List<ExtensionRenderer> extensionRenderers) {

  public ResolverHooks {
    Objects.requireNonNull(resources, "resources");
    extensionRenderers = List.copyOf(extensionRenderers);
  }

  public static ResolverHooks defaults() {
    return new ResolverHooks(ResourceResolvers.defaults(), null, List.of());
  }

  public @Nullable MediaResolver mediaResolver() {
    return resources.mediaResolver();
  }

  public @Nullable AttachmentResolver attachmentResolver() {
    return resources.attachmentResolver();
  }

  public @Nullable FileResolver fileResolver() {
    return resources.fileResolver();
  }

  public @Nullable PageLinkResolver pageLinkResolver() {
    return resources.pageLinkResolver();
  }

  public @Nullable PageTreeResolver pageTreeResolver() {
    return resources.pageTreeResolver();
  }

  public @Nullable ExcerptContentResolver excerptContentResolver() {
    return resources.excerptContentResolver();
  }

  public ResolverHooks withResources(ResourceResolvers value) {
    return new ResolverHooks(value, excerptResolver, extensionRenderers);
  }

  public ResolverHooks withExcerptResolver(@Nullable ExcerptResolver value) {
    return new ResolverHooks(resources, value, extensionRenderers);
  }

  public ResolverHooks withExtensionRenderers(List<ExtensionRenderer> value) {
    return new ResolverHooks(resources, excerptResolver, value);
  }
}
