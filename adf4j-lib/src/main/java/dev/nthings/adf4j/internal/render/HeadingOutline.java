package dev.nthings.adf4j.internal.render;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import dev.nthings.adf4j.HeadingReference;
import dev.nthings.adf4j.ast.Heading;

public final class HeadingOutline {

  private static final HeadingOutline EMPTY = new HeadingOutline(List.of(), new IdentityHashMap<>());

  private final List<HeadingReference> headings;
  private final Map<Heading, HeadingReference> headingsByNode;

  private HeadingOutline(
      List<HeadingReference> headings, Map<Heading, HeadingReference> headingsByNode) {
    this.headings = headings == null ? List.of() : List.copyOf(headings);
    this.headingsByNode = headingsByNode == null ? new IdentityHashMap<>() : headingsByNode;
  }

  public static HeadingOutline empty() {
    return EMPTY;
  }

  static HeadingOutline of(
      List<HeadingReference> headings, IdentityHashMap<Heading, HeadingReference> headingsByNode) {
    if ((headings == null || headings.isEmpty())
        && (headingsByNode == null || headingsByNode.isEmpty())) {
      return EMPTY;
    }
    return new HeadingOutline(headings, new IdentityHashMap<>(headingsByNode));
  }

  public List<HeadingReference> headings() {
    return headings;
  }

  HeadingReference infoFor(Heading heading) {
    return headingsByNode.get(heading);
  }
}
