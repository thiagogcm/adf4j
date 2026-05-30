package dev.nthings.adf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.nthings.adf4j.model.ParseIssue;
import dev.nthings.adf4j.model.ParseResult;
import dev.nthings.adf4j.parser.AdfAstParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class AdfParsingService {

  private static final Logger log = LoggerFactory.getLogger(AdfParsingService.class);

  private final JsonMapper mapper;
  private final AdfAstParser astParser;

  AdfParsingService(JsonMapper mapper) {
    this(mapper, new AdfAstParser(mapper));
  }

  AdfParsingService(JsonMapper mapper, AdfAstParser astParser) {
    this.mapper = mapper;
    this.astParser = astParser;
  }

  ParseResult parse(String rawAdf) {
    if (rawAdf == null || rawAdf.isBlank()) {
      log.debug("parse called with null or blank input – returning empty result");
      return ParseResult.empty();
    }

    log.debug("Parsing ADF payload ({} chars)", rawAdf.length());
    try {
      var jsonRoot = mapper.readTree(rawAdf);
      var issues = validateRoot(jsonRoot);
      if (!issues.isEmpty()) {
        issues.forEach(issue -> log.warn("ADF validation issue [{}]: {}", issue.code(), issue.message()));
        return new ParseResult(null, issues, false);
      }
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
    if (versionNode == null || !versionNode.canConvertToInt()) {
      issues.add(
          new ParseIssue("INVALID_VERSION", "ADF root must contain integer version.", null));
    } else if (versionNode.asInt() != 1) {
      issues.add(new ParseIssue("UNSUPPORTED_VERSION", "ADF version must be 1.", null));
    }

    var contentNode = document.get("content");
    if (contentNode == null || !contentNode.isArray()) {
      issues.add(
          new ParseIssue("INVALID_CONTENT", "ADF root must contain array field 'content'.", null));
    }

    return List.copyOf(issues);
  }
}
