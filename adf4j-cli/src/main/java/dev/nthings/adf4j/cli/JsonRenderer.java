package dev.nthings.adf4j.cli;

import java.util.Collection;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/// Selects metadata sections from the shared result projection.
final class JsonRenderer {
  static final List<String> SECTION_NAMES =
      List.of(
          "pageIds",
          "externalUrls",
          "mentionRefs",
          "attachmentRefs",
          "referencedFileIds",
          "pageTreeRefs",
          "excerptRefs",
          "excerpts",
          "outline");

  private JsonRenderer() {}

  static ObjectNode metadata(ObjectNode metadata, Collection<String> selected) {
    return metadata.retain(selected);
  }
}
