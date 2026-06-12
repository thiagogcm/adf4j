package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.result.UnresolvedReferences;

/**
 * Collects the lookups the active resolvers declined during one render: page node ids a
 * {@code PageLinkResolver} returned nothing for, and tree/excerpt macros that fell back to their
 * placeholder token. Fresh per render and driven by a single-threaded traversal, so no
 * synchronization.
 */
final class UnresolvedTracker {

  private final Set<String> pageIds = new LinkedHashSet<>();
  private final List<PageTreeReference> pageTreeRefs = new ArrayList<>();
  private final List<ExcerptIncludeReference> excerptRefs = new ArrayList<>();

  void recordPageId(String pageNodeId) {
    pageIds.add(pageNodeId);
  }

  void recordPageTree(PageTreeReference reference) {
    pageTreeRefs.add(reference);
  }

  void recordExcerpt(ExcerptIncludeReference reference) {
    excerptRefs.add(reference);
  }

  UnresolvedReferences build() {
    if (pageIds.isEmpty() && pageTreeRefs.isEmpty() && excerptRefs.isEmpty()) {
      return UnresolvedReferences.empty();
    }
    return new UnresolvedReferences(pageIds, pageTreeRefs, excerptRefs);
  }
}
