package dev.nthings.adf4j.options;

/**
 * Rewrites an inter-page link to a caller-supplied destination. Given the Confluence page node id
 * that the library extracts from a page link or page smart-card (the same ids surfaced as
 * {@code ContentMetadata.pageRefs}), return the URL or path the page landed at, or {@code null}/blank
 * to leave the original href untouched. Consulted for both text {@code link} marks and block/inline
 * page cards, letting a consumer that knows where each page was exported produce a fully cross-linked
 * offline copy.
 */
@FunctionalInterface
public interface PageLinkResolver {
  String resolve(String pageNodeId);
}
