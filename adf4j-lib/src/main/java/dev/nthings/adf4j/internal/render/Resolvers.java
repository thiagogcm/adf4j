package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.ast.AdfMedia;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.internal.ConfluenceMetadata;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.PageTreeEntry;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// The caller's hooks for one render: each method handles a missing hook, guards the call, applies
/// that hook's decline rule and records what stays unresolved. `null` always means the hook
/// declined or is absent, so the renderer falls back to its default.
///
/// URL hooks decline with a null or blank answer. A page-tree or excerpt answer is authoritative
/// even when empty, so only `null` declines, and those two lookups count as unresolved with no
/// resolver at all. A page lookup needs a resolver to count as unresolved.
final class Resolvers {

  private final MarkdownOptions options;
  private final UnresolvedTracker unresolved;

  Resolvers(MarkdownOptions options, UnresolvedTracker unresolved) {
    this.options = options;
    this.unresolved = unresolved;
  }

  /// The caller-resolved destination for an internal page href, or the original href when there is
  /// no `PageLinkResolver`, the href is not a page reference, or the resolver declines.
  String pageHref(String href, Attributes attrs) {
    var resolved = pageId(linkedPageId(href, attrs));
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
  /// declines; a decline is recorded as an unresolved page ref.
  @Nullable String pageId(@Nullable String pageNodeId) {
    var resolver = options.pageLinkResolver();
    if (resolver == null || pageNodeId == null || pageNodeId.isBlank()) {
      return null;
    }
    var resolved =
        CallbackGuards.guardNonBlank("PageLinkResolver", () -> resolver.resolve(pageNodeId));
    if (resolved == null) {
      unresolved.recordPageId(pageNodeId);
    }
    return resolved;
  }

  @Nullable String media(AdfMedia media) {
    var resolver = options.mediaResolver();
    return resolver == null
        ? null
        : CallbackGuards.guardNonBlank("MediaResolver", () -> resolver.resolve(media));
  }

  @Nullable String attachment(AttachmentReference reference) {
    var resolver = options.attachmentResolver();
    return resolver == null
        ? null
        : CallbackGuards.guardNonBlank("AttachmentResolver", () -> resolver.resolve(reference));
  }

  @Nullable List<PageTreeEntry> pageTree(PageTreeReference reference) {
    var resolver = options.pageTreeResolver();
    var entries =
        resolver == null
            ? null
            : CallbackGuards.guard("PageTreeResolver", () -> resolver.resolve(reference));
    if (entries == null) {
      unresolved.recordPageTree(reference);
    }
    return entries;
  }

  @Nullable String excerpt(ExcerptIncludeReference reference) {
    var resolver = options.excerptResolver();
    var resolved =
        resolver == null
            ? null
            : CallbackGuards.guard("ExcerptResolver", () -> resolver.resolve(reference));
    if (resolved == null) {
      unresolved.recordExcerpt(reference);
    }
    return resolved;
  }

  /// The first custom renderer's answer in order; `null` defers to the next, then the default.
  @Nullable String extension(AdfExtension node) {
    for (var renderer : options.extensionRenderers()) {
      var rendered = CallbackGuards.guard("ExtensionRenderer", () -> renderer.render(node));
      if (rendered != null) {
        return rendered;
      }
    }
    return null;
  }
}
