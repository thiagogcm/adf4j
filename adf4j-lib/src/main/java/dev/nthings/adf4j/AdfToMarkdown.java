package dev.nthings.adf4j;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.engine.AdfPipeline;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;

/**
 * Converts Atlassian Document Format (ADF) JSON to Markdown — the library's single entry point.
 * Immutable and thread-safe: the pipeline is built once per instance and reused, so configure once
 * with {@link #with(MarkdownOptions)} (or {@link #create()} for defaults) and reuse the instance.
 * When options vary per document, reuse one converter and pass them to the per-call
 * {@link #convert(String, MarkdownOptions)} overload instead of building a new converter each time.
 *
 * <p>To render the same document repeatedly (e.g. under different resolvers), parse once and reuse the
 * immutable result instead of paying the JSON parse per render:
 * {@snippet :
 * var parsed = converter.parse(adfJson);
 * var was = converter.convert(parsed, optionsAtT1);
 * var is = converter.convert(parsed, optionsAtT2);
 * }
 *
 * <p>The target is GitHub-Flavored Markdown. Some ADF constructs are lossy or by-design (dropped
 * visual marks, the table HTML fallback, synthetic {@code media:} placeholders). These behaviors,
 * URL scheme sanitization, and the available {@link MarkdownOptions} are documented in
 * {@code docs/markdown-conversion.md}.
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
  public ParseResult parse(@Nullable String adfJson) {
    return pipeline.parse(adfJson);
  }

  /**
   * Extracts {@link ContentMetadata} (references, attachments, outline) using the bound options,
   * running parse + analyze without rendering — e.g. to plan fetches from
   * {@link ContentMetadata#referencedFileIds()} before producing Markdown. Attachment-macro references
   * resolve against {@code options.confluenceContext()}, so supply the attachment context here too. Returns
   * {@link ContentMetadata#empty()} for blank or invalid input.
   */
  public ContentMetadata analyze(@Nullable String adfJson) {
    return pipeline.analyze(adfJson, options);
  }

  /** Extracts {@link ContentMetadata} from ADF JSON with options supplied for this call. */
  public ContentMetadata analyze(@Nullable String adfJson, MarkdownOptions perCallOptions) {
    return pipeline.analyze(adfJson, Objects.requireNonNull(perCallOptions, "options"));
  }

  /**
   * Extracts {@link ContentMetadata} from an already-parsed {@link AdfDocument} using bound options.
   * A {@code null} document yields {@link ContentMetadata#empty()}.
   */
  public ContentMetadata analyze(@Nullable AdfDocument document) {
    return pipeline.analyze(document, options);
  }

  /** Extracts {@link ContentMetadata} from an {@link AdfDocument} with options supplied for this call. */
  public ContentMetadata analyze(@Nullable AdfDocument document, MarkdownOptions perCallOptions) {
    return pipeline.analyze(document, Objects.requireNonNull(perCallOptions, "options"));
  }

  /** Converts ADF JSON to Markdown using the bound options. */
  public MarkdownResult convert(@Nullable String adfJson) {
    return pipeline.convert(adfJson, options);
  }

  /**
   * Converts a previously {@link #parse(String) parsed} document to Markdown using the bound options,
   * carrying the parse issues into the result's diagnostics — render one {@link ParseResult} any
   * number of times without re-parsing. A {@code null}/invalid result yields an empty body (plus the
   * configured {@code documentTitle}, if any) with the parse issues preserved.
   */
  public MarkdownResult convert(@Nullable ParseResult parsed) {
    return pipeline.convert(parsed, options);
  }

  /** Converts a previously parsed {@link ParseResult} with options supplied for this call. */
  public MarkdownResult convert(@Nullable ParseResult parsed, MarkdownOptions perCallOptions) {
    return pipeline.convert(parsed, Objects.requireNonNull(perCallOptions, "options"));
  }

  /**
   * Converts an already-parsed {@link AdfDocument} to Markdown using the bound options. A {@code null}
   * document yields an empty-body {@link MarkdownResult}. Prefer {@link #convert(ParseResult)} when
   * the document came from {@link #parse(String)}, so its parse issues reach the result.
   */
  public MarkdownResult convert(@Nullable AdfDocument document) {
    return pipeline.convert(document, options);
  }

  /**
   * Converts ADF JSON to Markdown with options supplied for this call, ignoring the bound options.
   * Lets a single reusable converter handle documents whose options differ (per-page media,
   * attachment, or page-link context) without rebuilding the pipeline.
   */
  public MarkdownResult convert(@Nullable String adfJson, MarkdownOptions perCallOptions) {
    return pipeline.convert(adfJson, Objects.requireNonNull(perCallOptions, "options"));
  }

  /** Converts an already-parsed {@link AdfDocument} to Markdown with options supplied for this call. */
  public MarkdownResult convert(@Nullable AdfDocument document, MarkdownOptions perCallOptions) {
    return pipeline.convert(document, Objects.requireNonNull(perCallOptions, "options"));
  }

  /** Convenience for {@code convert(adfJson).body()}. */
  public String toMarkdown(@Nullable String adfJson) {
    return convert(adfJson).body();
  }

  /** Convenience for {@code convert(adfJson, perCallOptions).body()}. */
  public String toMarkdown(@Nullable String adfJson, MarkdownOptions perCallOptions) {
    return convert(adfJson, perCallOptions).body();
  }
}
