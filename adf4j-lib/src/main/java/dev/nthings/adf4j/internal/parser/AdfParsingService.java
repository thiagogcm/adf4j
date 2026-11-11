package dev.nthings.adf4j.internal.parser;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.Diagnostic.Severity;
import dev.nthings.adf4j.result.ParseResult;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Reads and validates ADF JSON, then maps it to an {@link AdfDocument}. Owns the JSON
/// hardening behind {@link #createDefault()}.
public final class AdfParsingService {

  private static final Logger log = LoggerFactory.getLogger(AdfParsingService.class);

  // Caps nesting so a deep payload fails as INVALID_JSON rather than overflowing the stack:
  // every phase recurses per level. ~2 JSON levels per ADF block, so 100 admits ~50 nested
  // blocks.
  private static final int MAX_NESTING_DEPTH = 100;

  private final JsonMapper mapper;
  private final AdfAstParser astParser;

  AdfParsingService(JsonMapper mapper, AdfAstParser astParser) {
    this.mapper = mapper;
    this.astParser = astParser;
  }

  public static AdfParsingService createDefault() {
    var factory =
        JsonFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(MAX_NESTING_DEPTH).build())
            .build();
    // BigDecimal keeps every float digit; a double would round or overflow.
    var mapper =
        JsonMapper.builder(factory)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();
    return new AdfParsingService(mapper, new AdfAstParser(mapper));
  }

  /// A payload's JSON tree and its root diagnostics. `root` is null when they hold an error.
  public record Tree(@Nullable JsonNode root, List<Diagnostic> diagnostics) {}

  /// Reads and root-validates a payload without building the AST.
  public Tree readTree(@Nullable String rawAdf) {
    if (rawAdf == null || rawAdf.isBlank()) {
      log.debug("parse called with null or blank input – returning empty result");
      return new Tree(
          null, List.of(new Diagnostic(Diagnostic.Code.EMPTY_INPUT, "ADF input is blank.")));
    }

    log.debug("Parsing ADF payload ({} chars)", rawAdf.length());
    try {
      var jsonRoot = mapper.readTree(rawAdf);
      var issues = RootValidator.validate(jsonRoot);
      issues.forEach(
          issue -> log.debug("ADF validation issue [{}]: {}", issue.code(), issue.message()));
      var fatal = issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
      return new Tree(fatal ? null : jsonRoot, issues);
    } catch (JacksonException | NumberFormatException exception) {
      return new Tree(null, invalidJson(exception));
    }
  }

  public ParseResult parse(@Nullable String rawAdf) {
    var tree = readTree(rawAdf);
    var root = tree.root();
    if (root == null) {
      return new ParseResult(null, tree.diagnostics());
    }
    try {
      // Best-effort: issues stay in diagnostics as warnings.
      var parsed = astParser.parse(root);
      var allIssues = Stream.concat(tree.diagnostics().stream(), parsed.issues().stream()).toList();
      return new ParseResult(parsed.document(), allIssues);
    } catch (JacksonException | NumberFormatException exception) {
      return new ParseResult(null, invalidJson(exception));
    }
  }

  // A number beyond what the reader can hold surfaces as NumberFormatException.
  private static List<Diagnostic> invalidJson(RuntimeException exception) {
    log.debug("Failed to parse ADF JSON payload: {}", exception.getMessage());
    return List.of(
        new Diagnostic(
            Diagnostic.Code.INVALID_JSON, "Failed to parse ADF JSON payload.", exception));
  }
}
