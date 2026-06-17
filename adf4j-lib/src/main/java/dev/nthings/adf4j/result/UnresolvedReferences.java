package dev.nthings.adf4j.result;

import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// The lookups a render's resolvers declined, in first-seen order. `pageIds` are the page node
/// ids a configured `PageLinkResolver` was asked to resolve and declined (returned null/blank or
/// threw). Absent a resolver, no lookup happens and nothing is recorded. `pageTreeRefs` are the
/// `pagetree`/`children` macros that fell back to the `{{pagetree}}` /
/// `{{children}}` placeholder (no `PageTreeResolver`, a null return, or a throw); a
/// resolver answering with an empty list counts as resolved and is not listed. `excerptRefs`
/// are likewise the `excerpt-include` macros that fell back to their placeholder (no
/// `ExcerptResolver`, a null return, or a throw); an empty-string answer counts as resolved.
/// All empty means the output contains no declined-lookup artifacts.
public record UnresolvedReferences(
    Set<String> pageIds,
    List<PageTreeReference> pageTreeRefs,
    List<ExcerptIncludeReference> excerptRefs) {

  private static final UnresolvedReferences EMPTY =
      new UnresolvedReferences(Set.of(), List.of(), List.of());

  public UnresolvedReferences {
    pageIds =
        pageIds == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(pageIds));
    pageTreeRefs = pageTreeRefs == null ? List.of() : List.copyOf(pageTreeRefs);
    excerptRefs = excerptRefs == null ? List.of() : List.copyOf(excerptRefs);
  }

  public static UnresolvedReferences empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return pageIds.isEmpty() && pageTreeRefs.isEmpty() && excerptRefs.isEmpty();
  }
}
