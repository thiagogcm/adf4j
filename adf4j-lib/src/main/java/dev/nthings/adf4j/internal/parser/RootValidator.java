package dev.nthings.adf4j.internal.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.nthings.adf4j.result.ParseIssue;

import tools.jackson.databind.JsonNode;

/** Validates the ADF root node, reporting structural problems as {@link ParseIssue}s. */
final class RootValidator {

  private RootValidator() {
  }

  static List<ParseIssue> validate(JsonNode document) {
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
      issues.add(new ParseIssue("INVALID_ROOT_NODE", "ADF root node must have type 'doc'.", null));
    }

    var versionNode = document.get("version");
    var version = versionNode == null ? null : versionOf(versionNode);
    if (version == null) {
      issues.add(new ParseIssue("INVALID_VERSION", "ADF root must contain integer version.", null));
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
    if (versionNode.isString()) {
      try {
        return Integer.parseInt(versionNode.asString("").trim());
      } catch (NumberFormatException _) {
        return null;
      }
    }
    return null;
  }
}
