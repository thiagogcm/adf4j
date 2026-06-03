package dev.nthings.adf4j;

import java.util.Objects;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.engine.AdfPipeline;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;

/**
 * Converts Atlassian Document Format (ADF) JSON to Markdown. Immutable and thread-safe: configure
 * once with {@link #with(MarkdownOptions)} (or {@link #create()} for defaults) and reuse. For the
 * zero-config one-liner, see {@link Adf}.
 *
 * <p>The target is GitHub-Flavored Markdown. Some ADF constructs are lossy or by-design (dropped
 * visual marks, the table HTML fallback, synthetic {@code media:} placeholders, and the fact that
 * URL schemes are emitted verbatim and are <em>not</em> sanitized). These behaviors and the
 * available {@link MarkdownOptions} are documented in {@code docs/markdown-conversion.md}.
 */
public final class AdfToMarkdown {

  private final AdfPipeline pipeline;
  private final MarkdownOptions options;

  private AdfToMarkdown(MarkdownOptions options) {
    this.options = Objects.requireNonNull(options, "options");
    this.pipeline = AdfPipeline.createDefault();
  }

  /** A converter using {@link MarkdownOptions#defaults()}. */
  public static AdfToMarkdown create() {
    return new AdfToMarkdown(MarkdownOptions.defaults());
  }

  /** A converter bound to the given options. */
  public static AdfToMarkdown with(MarkdownOptions options) {
    return new AdfToMarkdown(options);
  }

  /** Parses ADF JSON into an {@link AdfDocument} with diagnostics, without rendering. */
  public ParseResult parse(String adfJson) {
    return pipeline.parse(adfJson);
  }

  /** Converts ADF JSON to Markdown, returning the body plus extracted metadata and diagnostics. */
  public MarkdownResult convert(String adfJson) {
    return pipeline.convert(adfJson, options);
  }

  /** Converts an already-parsed {@link AdfDocument} to Markdown. */
  public MarkdownResult convert(AdfDocument document) {
    return pipeline.convert(document, options);
  }

  /** Convenience for {@code convert(adfJson).body()}. */
  public String toMarkdown(String adfJson) {
    return convert(adfJson).body();
  }
}
