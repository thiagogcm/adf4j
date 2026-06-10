package dev.nthings.adf4j.result;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.nthings.adf4j.metadata.PageTreeReference;

/**
 * The lookups a render's resolvers declined, in first-seen order. {@code pageIds} are the page node
 * ids a configured {@code PageLinkResolver} was asked to resolve and declined (returned null/blank or
 * threw) — absent a resolver, no lookup happens and nothing is recorded. {@code pageTreeRefs} are the
 * {@code pagetree}/{@code children} macros that fell back to the {@code {{pagetree}}} /
 * {@code {{children}}} placeholder (no {@code PageTreeResolver}, a null return, or a throw); a
 * resolver answering with an empty list counts as resolved and is not listed. Both empty means the
 * output contains no declined-lookup artifacts.
 */
public record UnresolvedReferences(Set<String> pageIds, List<PageTreeReference> pageTreeRefs) {

  private static final UnresolvedReferences EMPTY = new UnresolvedReferences(Set.of(), List.of());

  public UnresolvedReferences {
    pageIds = pageIds == null
        ? Set.of()
        : Collections.unmodifiableSet(new LinkedHashSet<>(pageIds));
    pageTreeRefs = pageTreeRefs == null ? List.of() : List.copyOf(pageTreeRefs);
  }

  public static UnresolvedReferences empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return pageIds.isEmpty() && pageTreeRefs.isEmpty();
  }
}
