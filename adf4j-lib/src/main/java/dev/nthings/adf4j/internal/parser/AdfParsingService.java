package dev.nthings.adf4j.internal.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.nthings.adf4j.result.ParseIssue;
import dev.nthings.adf4j.result.ParseResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The parse phase: reads ADF JSON, validates the root, and maps it to an {@link dev.nthings.adf4j.ast.AdfDocument}
 * with diagnostics. Owns its JSON hardening (nesting-depth cap) and mapper behind {@link #createDefault()}.
 */
public final class AdfParsingService {

  private static final Logger log = LoggerFactory.getLogger(AdfParsingService.class);

  // Cap nesting so an adversarially deep payload surfaces as a caught INVALID_JSON, not a thrown error.
  private static final int MAX_NESTING_DEPTH = 1000;

  private final JsonMapper mapper;
  private final AdfAstParser astParser;

  AdfParsingService(JsonMapper mapper, AdfAstParser astParser) {
    this.mapper = mapper;
    this.astParser = astParser;
  }

  public static AdfParsingService createDefault() {
    var factory = JsonFactory.builder()
        .streamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(MAX_NESTING_DEPTH).build())
        .build();
    var mapper = JsonMapper.builder(factory).build();
    return new AdfParsingService(mapper, new AdfAstParser(mapper));
  }

  public ParseResult parse(String rawAdf) {
    if (rawAdf == null || rawAdf.isBlank()) {
      log.debug("parse called with null or blank input – returning empty result");
      return ParseResult.empty();
    }

    log.debug("Parsing ADF payload ({} chars)", rawAdf.length());
    try {
      var jsonRoot = mapper.readTree(rawAdf);
      var issues = validateRoot(jsonRoot);
      var fatal = issues.stream().anyMatch(issue -> !"UNSUPPORTED_VERSION".equals(issue.code()));
      issues.forEach(issue -> log.warn("ADF validation issue [{}]: {}", issue.code(), issue.message()));
      if (fatal) {
        return new ParseResult(null, issues, false);
      }
      // Only UNSUPPORTED_VERSION (or nothing): parse best-effort, keeping the warning in diagnostics.
      return new ParseResult(astParser.parseDocument(jsonRoot), issues, true);
    } catch (JacksonException exception) {
      log.error("Failed to parse ADF JSON payload: {}", exception.getMessage(), exception);
      return new ParseResult(
          null,
          List.of(
              new ParseIssue("INVALID_JSON", "Failed to parse ADF JSON payload.", exception)),
          false);
    }
  }

  private List<ParseIssue> validateRoot(JsonNode document) {
    if (document == null) {
      return List.of(new ParseIssue("MISSING_DOCUMENT", "ADF document is null.", null));
    }

    var issues = new ArrayList<ParseIssue>();
    if (!document.isObject()) {
      issues.add(new ParseIssue("INVALID_ROOT_TYPE", "ADF root must be an object.", null));
      return List.copyOf(issues);
    }

    var type = document.path("type").asString(null);
    if (!Objects.equals("doc", type)) {
      issues.add(
          new ParseIssue("INVALID_ROOT_NODE", "ADF root node must have type 'doc'.", null));
    }

    var versionNode = document.get("version");
    var version = versionNode == null ? null : versionOf(versionNode);
    if (version == null) {
      issues.add(
          new ParseIssue("INVALID_VERSION", "ADF root must contain integer version.", null));
    } else if (version != 1) {
      issues.add(new ParseIssue("UNSUPPORTED_VERSION", "ADF version must be 1.", null));
    }

    var contentNode = document.get("content");
    if (contentNode == null || !contentNode.isArray()) {
      issues.add(
          new ParseIssue("INVALID_CONTENT", "ADF root must contain array field 'content'.", null));
    }

    return List.copyOf(issues);
  }

  // Accept the version as a JSON number or a numeric string ("1"); anything else yields null.
  private static Integer versionOf(JsonNode versionNode) {
    if (versionNode.isNumber()) {
      return versionNode.asInt();
    }
    if (versionNode.isTextual()) {
      try {
        return Integer.parseInt(versionNode.asString("").trim());
      } catch (NumberFormatException _) {
        return null;
      }
    }
    return null;
  }
}
