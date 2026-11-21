package dev.nthings.adf4j.internal.resource;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfMedia;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.ConfluenceMetadata;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.ResourceResolvers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/// Resolves caller-owned resource destinations, page hierarchies, and AST excerpts without
/// formatting output. A `null` answer lets the renderer choose its fallback. One instance per
/// render: it memoizes excerpt answers and tracks the excerpts being expanded.
public final class ResourceResolver {
  private static final int MAX_EXCERPT_DEPTH = 8;

  /// Identifies an excerpt regardless of the including macro's parameters.
  private record ExcerptKey(String page, @Nullable String excerptName) {}

  private final ResourceResolvers hooks;
  private final AttachmentInventory attachments;
  private final CallbackTracker callbacks;
  private final Map<ExcerptIncludeReference, Optional<ExcerptDefinition>> excerpts =
      new HashMap<>();
  private final Set<ExcerptKey> expanding = new HashSet<>();

  public ResourceResolver(
      ResourceResolvers hooks, AttachmentInventory attachments, CallbackTracker callbacks) {
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

  /// Parsed excerpt AST blocks, or `null` when absent or declined. Answers are memoized per
  /// render; a failed call is not, so each occurrence reports its own failure.
  public @Nullable ExcerptDefinition excerptContent(
      ExcerptIncludeReference reference, String path) {
    var resolver = hooks.excerptContentResolver();
    if (resolver == null) {
      return null;
    }
    var cached = excerpts.get(reference);
    if (cached != null) {
      return cached.orElse(null);
    }
    var failuresBefore = callbacks.failureCount();
    var answer = callbacks.call("ExcerptContentResolver", path, () -> resolver.resolve(reference));
    if (callbacks.failureCount() == failuresBefore) {
      excerpts.put(reference, Optional.ofNullable(answer));
    }
    return answer;
  }

  /// Resolves an excerpt's AST and renders it with `render`. `null` without calling the hook when
  /// the excerpt is already being expanded (a cycle) or the nesting limit is reached, and `null`
  /// when the content is absent or declined, so the renderer chooses its fallback. Empty content
  /// renders as `render(List.of())`, which suppresses the macro rather than leaving a placeholder.
  public <T> @Nullable T expandExcerpt(
      ExcerptIncludeReference reference, String path, Function<List<AdfBlock>, T> render) {
    var key = new ExcerptKey(reference.page(), reference.excerptName());
    if (expanding.contains(key) || expanding.size() >= MAX_EXCERPT_DEPTH) {
      return null;
    }
    var excerpt = excerptContent(reference, path);
    if (excerpt == null) {
      return null;
    }
    expanding.add(key);
    try {
      return render.apply(excerpt.content());
    } finally {
      expanding.remove(key);
    }
  }

  /// Media destination: the node's own URL, then the media hook, then the file hook, then the
  /// inventory download URL. `null` when none answers.
  public @Nullable String mediaSource(AdfMedia media, String path) {
    var url = blankToNull(media.url());
    if (url != null) {
      return url;
    }
    var resolved = resolveMedia(media, path);
    if (resolved != null) {
      return resolved;
    }
    var attachment = attachments.byFileId(media.id());
    return attachment == null ? null : blankToNull(attachment.downloadUrl());
  }

  /// Attachment destination: the attachment hook, then the file hook, then the reference's
  /// inventory download URL. `null` when none answers.
  public @Nullable String attachmentSource(AttachmentReference reference, String path) {
    var resolved = resolveAttachment(reference, path);
    if (resolved != null) {
      return resolved;
    }
    return blankToNull(reference.downloadUrl());
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
    return blankToNull(callbacks.call(hook, path, callback));
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
