package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.internal.analyze.HeadingContent;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import org.jspecify.annotations.Nullable;

/// Markdown anchor policy for one heading occurrence. Heading facts come from the outline; only
/// this rule decides when a Markdown anchor is emitted.
final class HeadingAnchors {

  private HeadingAnchors() {}

  /// The anchor id to emit before `heading`, or `null`. An explicit anchor is always kept, even
  /// when the heading rendered blank. Other headings get their generated anchor only when a toc
  /// macro covers their level, so toc links don't depend on the consumer's slugger matching
  /// commonmark. `renderedBlank` comes from the renderer, since options decide what renders.
  static @Nullable String anchorToInject(
      HeadingOutline outline, Heading heading, String path, boolean renderedBlank) {
    var explicit = HeadingContent.extractAnchorId(heading.content());
    if (explicit != null || renderedBlank) {
      return explicit;
    }
    var reference = outline.referenceAt(path);
    if (reference == null || !outline.isTocReferenced(heading.level())) {
      return null;
    }
    return reference.anchor();
  }
}
