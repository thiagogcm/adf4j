package dev.nthings.adf4j;

import java.util.List;

public record RenderResult(String body, ContentMetadata metadata, List<ParseIssue> diagnostics) {

  public RenderResult {
    body = body == null ? "" : body;
    metadata = metadata == null ? ContentMetadata.empty() : metadata;
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  public static RenderResult empty() {
    return new RenderResult("", ContentMetadata.empty(), List.of());
  }
}
