package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.model.ParseIssue;

public record RenderResult(
    String body,
    OutputFormat outputFormat,
    ContentMetadata metadata,
    List<ParseIssue> diagnostics) {

  public RenderResult {
    body = body == null ? "" : body;
    metadata = metadata == null ? ContentMetadata.empty() : metadata;
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  public static RenderResult empty(OutputFormat outputFormat) {
    return new RenderResult("", outputFormat, ContentMetadata.empty(), List.of());
  }
}
