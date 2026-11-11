package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/// Rewrites a page link from its Confluence page node ID. Null or blank, or an exception (which
/// emits a diagnostic), keeps the original URL. Also used for page cards and page-tree entries.
@FunctionalInterface
public interface PageLinkResolver {
  @Nullable String resolve(String pageNodeId);
}
