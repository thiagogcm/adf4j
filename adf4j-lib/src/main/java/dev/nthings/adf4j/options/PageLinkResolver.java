package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/// Rewrites a page link using the Confluence page node ID from source metadata.
/// Null or blank keeps the original URL. Exceptions emit a diagnostic and keep it too.
/// Used for text links, page cards, and resolved page-tree entries.
@FunctionalInterface
public interface PageLinkResolver {
  @Nullable String resolve(String pageNodeId);
}
