package dev.nthings.adf4j.result;

import java.util.List;

import dev.nthings.adf4j.metadata.ContentMetadata;

/** The output of an ADF-to-Markdown conversion: the body plus extracted metadata and diagnostics. */
public record MarkdownResult(String body, ContentMetadata metadata, List<ParseIssue> diagnostics) {

  public MarkdownResult {
    body = body == null ? "" : body;
    metadata = metadata == null ? ContentMetadata.empty() : metadata;
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  public static MarkdownResult empty() {
    return new MarkdownResult("", ContentMetadata.empty(), List.of());
  }
}
