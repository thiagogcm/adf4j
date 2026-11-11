package dev.nthings.adf4j.internal.resource;

import dev.nthings.adf4j.ast.AdfMedia;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.ConfluenceMetadata;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.ResolverHooks;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/// Resolves caller-owned resource destinations and page hierarchies without formatting output.
/// A `null` answer lets the renderer choose its fallback.
public final class ResourceResolver {

  private final ResolverHooks hooks;
  private final AttachmentInventory attachments;
  private final CallbackTracker callbacks;

  public ResourceResolver(
      ResolverHooks hooks, AttachmentInventory attachments, CallbackTracker callbacks) {
    this.hooks = Objects.requireNonNull(hooks, "hooks");
    this.attachments = Objects.requireNonNull(attachments, "attachments");
    this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
  }

  /// Resolved destination for an internal page href; the original href if there is no
  /// `PageLinkResolver`, it is not a page reference, or the resolver declines.
  public String pageHref(String href, Attributes attrs, String path) {
    var resolved = pageId(linkedPageId(href, attrs), path);
    return resolved == null ? href : resolved;
  }

  /// Page node id {@link #pageHref} looks up, or `null`. Calls no hook, so safe to compare links.
  public @Nullable String linkedPageId(String href, Attributes attrs) {
    return hooks.pageLinkResolver() == null
        ? null
        : ConfluenceSupport.pageNodeId(href, ConfluenceMetadata.from(attrs));
  }

  /// Resolver destination for a page node id; `null` when absent or declined.
  public @Nullable String pageId(@Nullable String pageNodeId, String path) {
    var resolver = hooks.pageLinkResolver();
    if (resolver == null || pageNodeId == null || pageNodeId.isBlank()) {
      return null;
    }
    return nonBlank("PageLinkResolver", path, () -> resolver.resolve(pageNodeId));
  }

  /// Page hierarchy entries, or `null` when absent or declined. An empty list is a resolved,
  /// empty hierarchy rather than a missing answer.
  public @Nullable List<PageTreeEntry> pageTree(PageTreeReference reference, String path) {
    var resolver = hooks.pageTreeResolver();
    return resolver == null
        ? null
        : callbacks.call("PageTreeResolver", path, () -> resolver.resolve(reference));
  }

  /// Media destination: the node's own URL, then the media hook, then the file hook, then the
  /// inventory download URL. `null` when none answers.
  public @Nullable String mediaSource(AdfMedia media, String path) {
    var url = media.url();
    if (url != null && !url.isBlank()) {
      return url;
    }
    var resolved = resolveMedia(media, path);
    if (resolved != null) {
      return resolved;
    }
    var attachment = attachments.byFileId(media.id());
    var downloadUrl = attachment == null ? null : attachment.downloadUrl();
    return downloadUrl == null || downloadUrl.isBlank() ? null : downloadUrl;
  }

  /// Attachment destination: the attachment hook, then the file hook, then the reference's
  /// inventory download URL. `null` when none answers.
  public @Nullable String attachmentSource(AttachmentReference reference, String path) {
    var resolved = resolveAttachment(reference, path);
    if (resolved != null) {
      return resolved;
    }
    var downloadUrl = reference.downloadUrl();
    return downloadUrl == null || downloadUrl.isBlank() ? null : downloadUrl;
  }

  private @Nullable String resolveMedia(AdfMedia media, String path) {
    var resolver = hooks.mediaResolver();
    var resolved =
        resolver == null ? null : nonBlank("MediaResolver", path, () -> resolver.resolve(media));
    return resolved != null ? resolved : file(media.id(), path);
  }

  private @Nullable String resolveAttachment(AttachmentReference reference, String path) {
    var resolver = hooks.attachmentResolver();
    var resolved =
        resolver == null
            ? null
            : nonBlank("AttachmentResolver", path, () -> resolver.resolve(reference));
    return resolved != null ? resolved : file(reference.fileId(), path);
  }

  private @Nullable String file(@Nullable String id, String path) {
    var resolver = hooks.fileResolver();
    return resolver == null || id == null
        ? null
        : nonBlank("FileResolver", path, () -> resolver.resolve(id));
  }

  private @Nullable String nonBlank(String hook, String path, Supplier<@Nullable String> callback) {
    var value = callbacks.call(hook, path, callback);
    return value == null || value.isBlank() ? null : value;
  }
}
