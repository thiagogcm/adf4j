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

  /**
   * {@code true} when any diagnostic is a {@link ParseIssue.Severity#WARNING} or
   * {@link ParseIssue.Severity#ERROR} — i.e. the document did not convert cleanly. This covers
   * structural parse problems, content dropped/placeholdered under the active
   * {@code UnknownNodePolicy}, and unsupported marks dropped from the output. A convenience for
   * flagging documents to review without inspecting every diagnostic.
   *
   * <p>It does <em>not</em> flag by-design, configuration-driven lossiness — synthetic
   * {@code media:}/{@code attachment:} placeholders emitted when no resolver is supplied, visual-only
   * marks dropped when {@code htmlVisualMarks} is off, or the table HTML fallback — since those are
   * controlled by {@code MarkdownOptions} rather than being defects in the input.
   */
  public boolean wasLossy() {
    return diagnostics.stream()
        .map(ParseIssue::severity)
        .anyMatch(severity -> severity == ParseIssue.Severity.WARNING
            || severity == ParseIssue.Severity.ERROR);
  }
}
