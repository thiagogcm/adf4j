package dev.nthings.adf4j.internal.parser;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.Diagnostic.Code;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/// Validates the ADF root node, reporting structural problems as {@link Diagnostic}s.
final class RootValidator {

  private RootValidator() {}

  static List<Diagnostic> validate(JsonNode document) {
    if (!document.isObject()) {
      return List.of(new Diagnostic(Code.INVALID_ROOT_TYPE, "ADF root must be an object.").at(""));
    }

    var issues = new ArrayList<Diagnostic>();
    var type = document.path("type").asString(null);
    if (!Objects.equals("doc", type)) {
      issues.add(
          new Diagnostic(Code.INVALID_ROOT_NODE, "ADF root node must have type 'doc'.")
              .at("/type"));
    }

    var versionNode = document.get("version");
    var version = versionNode == null ? null : versionOf(versionNode);
    if (version == null) {
      issues.add(
          new Diagnostic(Code.INVALID_VERSION, "ADF root must contain integer version.")
              .at("/version"));
    } else if (version != 1) {
      // Non-fatal: the document still parses and renders best-effort, so flag it as a warning.
      issues.add(new Diagnostic(Code.UNSUPPORTED_VERSION, "ADF version must be 1.").at("/version"));
    }

    var contentNode = document.get("content");
    if (contentNode == null || !contentNode.isArray()) {
      issues.add(
          new Diagnostic(Code.INVALID_CONTENT, "ADF root must contain array field 'content'.")
              .at("/content"));
    }
    return List.copyOf(issues);
  }

  // Accept the version as a whole JSON number or a numeric string ("1"); anything else yields null.
  private static @Nullable Integer versionOf(JsonNode versionNode) {
    if (versionNode.isNumber()) {
      return versionNode.canConvertToExactIntegral() ? versionNode.asInt() : null;
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
