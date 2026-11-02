package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.engine.AdfPipeline;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.metadata.ExcerptDefinition;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.result.MarkdownResult;
import dev.nthings.adf4j.result.ParseResult;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Converts Atlassian Document Format (ADF) to Markdown. {@link AdfJson} is the library's other
/// entry point, for reading and writing ADF JSON. Immutable and thread-safe. An instance is just
/// its bound options, over one pipeline shared by the whole process, so it is cheap to create: bind
/// a new converter with {@link #with(MarkdownOptions)} (or {@link #create()} for defaults) whenever
/// the options vary per document.
///
/// To render the same document repeatedly (e.g. under different resolvers), parse once and reuse
/// the immutable result instead of paying the JSON parse per render:
/// {@snippet :
/// var parsed = AdfJson.parse(adfJson);
/// var was = AdfToMarkdown.with(optionsAtT1).convert(parsed);
/// var now = AdfToMarkdown.with(optionsAtT2).convert(parsed);
/// }
///
/// The target is GitHub-Flavored Markdown. Some ADF constructs are lossy or by-design (dropped
/// visual marks, the table HTML fallback, synthetic `media:` placeholders).
public final class AdfToMarkdown {

  private static final AdfPipeline PIPELINE = AdfPipeline.createDefault();

  private final MarkdownOptions options;

  private AdfToMarkdown(MarkdownOptions options) {
    this.options = Objects.requireNonNull(options, "options");
  }

  /// A converter using {@link MarkdownOptions#defaults()}.
  public static AdfToMarkdown create() {
    return new AdfToMarkdown(MarkdownOptions.defaults());
  }

  /// A converter bound to the given options.
  public static AdfToMarkdown with(MarkdownOptions options) {
    return new AdfToMarkdown(options);
  }

  /// Converts ADF JSON to Markdown. Blank or invalid input yields an empty body (plus the
  // configured
  /// `documentTitle`, if any) with its diagnostics.
  public MarkdownResult convert(@Nullable String adfJson) {
    return PIPELINE.convert(adfJson, options);
  }

  /// Converts a document previously parsed by {@link AdfJson#parse(String)}, carrying the parse
  /// diagnostics into the result. Render one {@link ParseResult} any number of times without
  /// re-parsing. An invalid result yields an empty body (plus the configured `documentTitle`, if
  /// any) with the parse diagnostics preserved.
  public MarkdownResult convert(ParseResult parsed) {
    return PIPELINE.convert(Objects.requireNonNull(parsed, "parsed"), options);
  }

  /// Converts an already-parsed {@link AdfDocument} to Markdown. Prefer
  /// {@link #convert(ParseResult)} when the document came from {@link AdfJson#parse(String)}, so
  /// its parse diagnostics reach the result.
  public MarkdownResult convert(AdfDocument document) {
    return PIPELINE.convert(Objects.requireNonNull(document, "document"), options);
  }

  /// Converts an excerpt's own content. A `documentTitle` belongs to a whole page, so it is ignored
  /// here.
  public MarkdownResult convert(ExcerptDefinition excerpt) {
    var document = new AdfDocument(1, Objects.requireNonNull(excerpt, "excerpt").content());
    return PIPELINE.convert(document, options.withDocumentTitle(null));
  }

  /// Extracts {@link ContentMetadata} (references, attachments, outline) without rendering, e.g. to
  /// plan fetches from {@link ContentMetadata#referencedFileIds()} before producing Markdown.
  /// Attachment references resolve against `options.attachments()`, so supply the inventory here
  /// too. An invalid result yields {@link ContentMetadata#empty()}.
  public ContentMetadata analyze(ParseResult parsed) {
    return PIPELINE.analyze(Objects.requireNonNull(parsed, "parsed"), options);
  }

  /// Extracts {@link ContentMetadata} from an already-parsed {@link AdfDocument}, as
  /// {@link #analyze(ParseResult)} does.
  public ContentMetadata analyze(AdfDocument document) {
    return PIPELINE.analyze(Objects.requireNonNull(document, "document"), options);
  }

  /// Convenience for `convert(adfJson).body()`.
  public String toMarkdown(@Nullable String adfJson) {
    return convert(adfJson).body();
  }
}
