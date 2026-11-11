package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.ast.AdfMedia;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.internal.ConfluenceMetadata;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.PageTreeEntry;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/// Invokes caller hooks, reporting callback failures. Renderers record missing dependencies after
/// every fallback.
final class Resolvers {

  private final RenderEvents events;
  private final ConversionOptions options;

  Resolvers(ConversionOptions options, RenderEvents events) {
    this.options = options;
    this.events = events;
  }

  /// Resolved destination for an internal page href; the original href if there is no
  /// `PageLinkResolver`, it is not a page reference, or the resolver declines.
  String pageHref(String href, Attributes attrs, String path) {
    var resolved = pageId(linkedPageId(href, attrs), path);
    return resolved == null ? href : resolved;
  }

  /// Page node id {@link #pageHref} looks up, or `null`. Calls no hook, so safe to compare links.
  @Nullable String linkedPageId(String href, Attributes attrs) {
    return options.pageLinkResolver() == null
        ? null
        : ConfluenceSupport.pageNodeId(href, ConfluenceMetadata.from(attrs));
  }

  /// Resolver destination for a page node id; `null` when absent or declined.
  @Nullable String pageId(@Nullable String pageNodeId, String path) {
    var resolver = options.pageLinkResolver();
    if (resolver == null || pageNodeId == null || pageNodeId.isBlank()) {
      return null;
    }
    return nonBlank("PageLinkResolver", path, () -> resolver.resolve(pageNodeId));
  }

  private @Nullable String nonBlank(String hook, String path, Supplier<@Nullable String> callback) {
    var value = events.call(hook, path, callback);
    return value == null || value.isBlank() ? null : value;
  }

  @Nullable String media(AdfMedia media, String path) {
    var resolver = options.mediaResolver();
    var resolved =
        resolver == null ? null : nonBlank("MediaResolver", path, () -> resolver.resolve(media));
    return resolved != null ? resolved : file(media.id(), path);
  }

  @Nullable String attachment(AttachmentReference reference, String path) {
    var resolver = options.attachmentResolver();
    var resolved =
        resolver == null
            ? null
            : nonBlank("AttachmentResolver", path, () -> resolver.resolve(reference));
    return resolved != null ? resolved : file(reference.fileId(), path);
  }

  private @Nullable String file(@Nullable String id, String path) {
    var resolver = options.fileResolver();
    return resolver == null || id == null
        ? null
        : nonBlank("FileResolver", path, () -> resolver.resolve(id));
  }

  @Nullable List<PageTreeEntry> pageTree(PageTreeReference reference, String path) {
    var resolver = options.pageTreeResolver();
    return resolver == null
        ? null
        : events.call("PageTreeResolver", path, () -> resolver.resolve(reference));
  }

  @Nullable String excerpt(ExcerptIncludeReference reference, String path) {
    var resolver = options.excerptResolver();
    return resolver == null
        ? null
        : events.call("ExcerptResolver", path, () -> resolver.resolve(reference));
  }

  /// First non-deferred answer; `null` falls through to the built-in handler.
  @Nullable ExtensionOutput extension(AdfExtension node, String path) {
    for (var renderer : options.extensionRenderers()) {
      var rendered = events.call("ExtensionRenderer", path, () -> renderer.render(node));
      if (rendered != null && rendered.mode() != ExtensionOutput.Mode.DEFER) {
        return rendered;
      }
    }
    return null;
  }
}
