package dev.nthings.adf4j.options;

import org.jspecify.annotations.Nullable;

/// Rewrites an inter-page link to a caller-supplied destination. Given the Confluence page node id
/// that the library extracts from a page link or page smart-card (the same ids surfaced as
/// `ContentMetadata.pageRefs`), return the URL or path the page landed at, or `null`/blank to
/// leave the original href untouched (a thrown `RuntimeException` is logged and also leaves it as
/// is). Consulted for both text `link` marks and block/inline page cards, letting a consumer that
/// knows where each page was exported produce a fully cross-linked offline copy.
@FunctionalInterface
public interface PageLinkResolver {
  @Nullable String resolve(String pageNodeId);
}
