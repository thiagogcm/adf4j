package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.metadata.ContentMetadata;

/** Result of the single analysis pass: the heading outline (for the renderer) and content metadata. */
public record DocumentAnalysis(HeadingOutline outline, ContentMetadata metadata) {

  public static DocumentAnalysis empty() {
    return new DocumentAnalysis(HeadingOutline.empty(), ContentMetadata.empty());
  }
}
