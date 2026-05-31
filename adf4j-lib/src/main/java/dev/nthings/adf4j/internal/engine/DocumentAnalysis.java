package dev.nthings.adf4j.internal.engine;

import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.internal.render.HeadingOutline;

/** Result of the single analysis pass: the heading outline (for the renderer) and content metadata. */
record DocumentAnalysis(HeadingOutline outline, ContentMetadata metadata) {

  static DocumentAnalysis empty() {
    return new DocumentAnalysis(HeadingOutline.empty(), ContentMetadata.empty());
  }
}
