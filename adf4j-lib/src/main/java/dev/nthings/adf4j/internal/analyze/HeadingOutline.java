package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.metadata.HeadingReference;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Heading model built by analyze and consumed by render.
public final class HeadingOutline {

  private static final HeadingOutline EMPTY =
      new HeadingOutline(List.of(), new IdentityHashMap<>(), Set.of());

  private final List<HeadingReference> headings;
  private final Map<Heading, HeadingReference> headingsByNode;
  // Clamped levels covered by a toc macro; their headings get an injected anchor.
  private final Set<Integer> tocReferencedLevels;

  private HeadingOutline(
      List<HeadingReference> headings,
      Map<Heading, HeadingReference> headingsByNode,
      Set<Integer> tocReferencedLevels) {
    this.headings = List.copyOf(headings);
    this.headingsByNode = headingsByNode;
    this.tocReferencedLevels = Set.copyOf(tocReferencedLevels);
  }

  public static HeadingOutline empty() {
    return EMPTY;
  }

  // Takes ownership of headingsByNode; callers must not reuse it.
  static HeadingOutline of(
      List<HeadingReference> headings,
      IdentityHashMap<Heading, HeadingReference> headingsByNode,
      Set<Integer> tocReferencedLevels) {
    if (headings.isEmpty()) {
      return EMPTY;
    }
    return new HeadingOutline(headings, headingsByNode, tocReferencedLevels);
  }

  public List<HeadingReference> headings() {
    return headings;
  }

  /// Anchor id to emit for a heading, or `null`. An explicit anchor is always kept, even when
  /// the heading rendered blank. Other headings get their generated anchor only when a toc macro
  /// covers their level, so toc links don't depend on the consumer's slugger matching commonmark.
  /// `renderedBlank` comes from the renderer, since options decide what renders.
  public @Nullable String anchorToInject(Heading heading, boolean renderedBlank) {
    var explicit = HeadingContent.extractAnchorId(heading.content());
    if (explicit != null || renderedBlank) {
      return explicit;
    }
    var reference = headingsByNode.get(heading);
    return reference != null && isTocReferenced(heading) ? reference.anchor() : null;
  }

  boolean isTocReferenced(Heading heading) {
    return tocReferencedLevels.contains(AdfText.clampHeadingLevel(heading.level()));
  }
}
