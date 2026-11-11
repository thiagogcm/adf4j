package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.metadata.HeadingReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Backend-neutral heading facts: outline order, source occurrence paths, and toc coverage.
/// Markdown anchor injection is a render policy and is not part of this model.
public final class HeadingOutline {

  private static final HeadingOutline EMPTY = new HeadingOutline(List.of(), Map.of(), Set.of());

  private final List<HeadingReference> headings;
  private final Map<String, HeadingReference> referencesByPath;
  // Clamped levels covered by a toc macro, independent of anchor emission policy.
  private final Set<Integer> tocReferencedLevels;

  private HeadingOutline(
      List<HeadingReference> headings,
      Map<String, HeadingReference> referencesByPath,
      Set<Integer> tocReferencedLevels) {
    this.headings = List.copyOf(headings);
    this.referencesByPath = Map.copyOf(referencesByPath);
    this.tocReferencedLevels = Set.copyOf(tocReferencedLevels);
  }

  public static HeadingOutline empty() {
    return EMPTY;
  }

  static HeadingOutline of(
      List<HeadingReference> headings,
      Map<String, HeadingReference> referencesByPath,
      Set<Integer> tocReferencedLevels) {
    if (headings.isEmpty()) {
      return EMPTY;
    }
    return new HeadingOutline(headings, referencesByPath, tocReferencedLevels);
  }

  public List<HeadingReference> headings() {
    return headings;
  }

  /// The heading collected at `path`, a JSON Pointer such as `/content/1/content/0`, or `null`
  /// when that occurrence produced no outline entry. Lookup follows source occurrence, so a node
  /// reused in two places has one reference per path.
  public @Nullable HeadingReference referenceAt(String path) {
    return referencesByPath.get(path);
  }

  /// Whether a toc macro covers `level`.
  public boolean isTocReferenced(int level) {
    return tocReferencedLevels.contains(AdfText.clampHeadingLevel(level));
  }
}
