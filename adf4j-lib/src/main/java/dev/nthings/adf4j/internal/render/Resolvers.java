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

/// Invokes caller hooks and reports callback failures. Renderers record missing dependencies
/// after trying every applicable fallback.
final class Resolvers {

  private final RenderEvents events;
  private final ConversionOptions options;

  Resolvers(ConversionOptions options, RenderEvents events) {
    this.options = options;
    this.events = events;
  }

  /// The caller-resolved destination for an internal page href, or the original href when there is
  /// no `PageLinkResolver`, the href is not a page reference, or the resolver declines.
  String pageHref(String href, Attributes attrs, String path) {
    var resolved = pageId(linkedPageId(href, attrs), path);
    return resolved == null ? href : resolved;
  }

  /// The page node id {@link #pageHref} would look up for this link, or `null` when it looks up
  /// nothing. Reads no hook, so it is free to call for comparing links.
  @Nullable String linkedPageId(String href, Attributes attrs) {
    return options.pageLinkResolver() == null
        ? null
        : ConfluenceSupport.pageNodeId(href, ConfluenceMetadata.from(attrs));
  }

  /// The resolver's destination for a page node id, or `null` when there is no id or resolver or it
  /// declines.
  @Nullable String pageId(@Nullable String pageNodeId, String path) {
    var resolver = options.pageLinkResolver();
    if (resolver == null || pageNodeId == null || pageNodeId.isBlank()) {
      return null;
    }
    var resolved = nonBlank("PageLinkResolver", path, () -> resolver.resolve(pageNodeId));
    return resolved;
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
    var entries =
        resolver == null
            ? null
            : events.call("PageTreeResolver", path, () -> resolver.resolve(reference));
    return entries;
  }

  @Nullable String excerpt(ExcerptIncludeReference reference, String path) {
    var resolver = options.excerptResolver();
    var resolved =
        resolver == null
            ? null
            : events.call("ExcerptResolver", path, () -> resolver.resolve(reference));
    return resolved;
  }

  /// The first non-deferred answer in order, then the built-in handler.
  @Nullable ExtensionOutput extension(AdfExtension node) {
    for (var renderer : options.extensionRenderers()) {
      var rendered =
          events.call("ExtensionRenderer", events.source.path(node), () -> renderer.render(node));
      if (rendered != null && rendered.mode() != ExtensionOutput.Mode.DEFER) {
        return rendered;
      }
    }
    return null;
  }
}
