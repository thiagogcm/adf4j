package dev.nthings.adf4j.internal.parser;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ParseResult;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/// The parse phase: reads ADF JSON, validates the root, and maps it to an {@link AdfDocument}
/// with diagnostics. Owns its JSON hardening (nesting-depth cap) and mapper behind
/// {@link #createDefault()}.
public final class AdfParsingService {

  private static final Logger log = LoggerFactory.getLogger(AdfParsingService.class);

  // Cap JSON nesting so a deep payload surfaces as a caught INVALID_JSON instead of overflowing the
  // stack (parse, AST build, analyze and render all recurse per level). ~2 JSON levels per ADF
  // block,
  // so 100 admits ~50 nested blocks — far beyond any real document, yet safe on small thread
  // stacks.
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
    var mapper = JsonMapper.builder(factory).build();
    return new AdfParsingService(mapper, new AdfAstParser(mapper));
  }

  public ParseResult parse(@Nullable String rawAdf) {
    if (rawAdf == null || rawAdf.isBlank()) {
      log.debug("parse called with null or blank input – returning empty result");
      return ParseResult.empty();
    }

    log.debug("Parsing ADF payload ({} chars)", rawAdf.length());
    try {
      var jsonRoot = mapper.readTree(rawAdf);
      var issues = RootValidator.validate(jsonRoot);
      var fatal = issues.stream().anyMatch(issue -> !"UNSUPPORTED_VERSION".equals(issue.code()));
      issues.forEach(
          issue -> log.warn("ADF validation issue [{}]: {}", issue.code(), issue.message()));
      if (fatal) {
        return new ParseResult(null, issues, false);
      }
      // Only UNSUPPORTED_VERSION (or nothing): parse best-effort, keeping the warning in
      // diagnostics.
      return new ParseResult(astParser.parseDocument(jsonRoot), issues, true);
    } catch (JacksonException exception) {
      log.error("Failed to parse ADF JSON payload: {}", exception.getMessage(), exception);
      return new ParseResult(
          null,
          List.of(new Diagnostic("INVALID_JSON", "Failed to parse ADF JSON payload.", exception)),
          false);
    }
  }
}
