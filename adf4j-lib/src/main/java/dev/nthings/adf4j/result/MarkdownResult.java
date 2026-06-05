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
   * {@link ParseIssue.Severity#ERROR} — i.e. the document did not convert cleanly: structural parse
   * problems, content dropped/placeholdered under the active {@code UnknownNodePolicy}, or unsupported
   * marks dropped. Gate "real loss" on this (or {@link ParseIssue#severity()}), not "any diagnostic
   * present" — some diagnostics are informational. Under
   * {@link dev.nthings.adf4j.options.UnknownNodePolicy#PRESERVE_RAW}:
   * <ul>
   *   <li>an unknown <em>node</em> is preserved as raw JSON ({@link ParseIssue.Severity#INFO}) — not lossy;
   *   <li>an unknown <em>mark</em> has no standalone form and is dropped ({@link ParseIssue.Severity#WARNING}) — lossy.
   * </ul>
   *
   * <p>It does <em>not</em> flag by-design, options-driven lossiness — {@code media:}/{@code attachment:}
   * placeholders when no resolver is set, visual-only marks dropped with {@code htmlVisualMarks} off, or
   * the table HTML fallback.
   */
  public boolean wasLossy() {
    return diagnostics.stream()
        .map(ParseIssue::severity)
        .anyMatch(severity -> severity == ParseIssue.Severity.WARNING
            || severity == ParseIssue.Severity.ERROR);
  }
}
