package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.metadata.HeadingReference;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// The analyze phase's heading model, consumed by the render phase.
public final class HeadingOutline {

  private static final HeadingOutline EMPTY =
      new HeadingOutline(List.of(), new IdentityHashMap<>(), Set.of());

  private final List<HeadingReference> headings;
  private final Map<Heading, HeadingReference> headingsByNode;
  // Clamped heading levels covered by at least one toc macro; their headings get an injected
  // anchor.
  private final Set<Integer> tocReferencedLevels;

  private HeadingOutline(
      @Nullable List<HeadingReference> headings,
      @Nullable Map<Heading, HeadingReference> headingsByNode,
      @Nullable Set<Integer> tocReferencedLevels) {
    this.headings = headings == null ? List.of() : List.copyOf(headings);
    this.headingsByNode = headingsByNode == null ? new IdentityHashMap<>() : headingsByNode;
    this.tocReferencedLevels =
        tocReferencedLevels == null ? Set.of() : Set.copyOf(tocReferencedLevels);
  }

  public static HeadingOutline empty() {
    return EMPTY;
  }

  static HeadingOutline of(
      @Nullable List<HeadingReference> headings,
      @Nullable IdentityHashMap<Heading, HeadingReference> headingsByNode,
      @Nullable Set<Integer> tocReferencedLevels) {
    if ((headings == null || headings.isEmpty())
        && (headingsByNode == null || headingsByNode.isEmpty())) {
      return EMPTY;
    }
    return new HeadingOutline(headings, new IdentityHashMap<>(headingsByNode), tocReferencedLevels);
  }

  public List<HeadingReference> headings() {
    return headings;
  }

  public @Nullable HeadingReference infoFor(Heading heading) {
    return headingsByNode.get(heading);
  }

  public boolean isTocReferenced(Heading heading) {
    return tocReferencedLevels.contains(AdfText.clampHeadingLevel(heading.level()));
  }
}
