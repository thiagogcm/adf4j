package dev.nthings.adf4j.internal.parser;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.Diagnostic.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/// Validates the ADF root node, reporting structural problems as {@link Diagnostic}s.
final class RootValidator {

  private RootValidator() {}

  static List<Diagnostic> validate(@Nullable JsonNode document) {
    if (document == null) {
      return List.of(new Diagnostic("MISSING_DOCUMENT", "ADF document is null.", null));
    }

    var issues = new ArrayList<Diagnostic>();
    if (!document.isObject()) {
      issues.add(new Diagnostic("INVALID_ROOT_TYPE", "ADF root must be an object.", null));
      return List.copyOf(issues);
    }

    var type = document.path("type").asString(null);
    if (!Objects.equals("doc", type)) {
      issues.add(new Diagnostic("INVALID_ROOT_NODE", "ADF root node must have type 'doc'.", null));
    }

    var versionNode = document.get("version");
    var version = versionNode == null ? null : versionOf(versionNode);
    if (version == null) {
      issues.add(new Diagnostic("INVALID_VERSION", "ADF root must contain integer version.", null));
    } else if (version != 1) {
      // Non-fatal: the document still parses and renders best-effort, so flag it as a warning.
      issues.add(
          new Diagnostic("UNSUPPORTED_VERSION", "ADF version must be 1.", null, Severity.WARNING));
    }

    var contentNode = document.get("content");
    if (contentNode == null || !contentNode.isArray()) {
      issues.add(
          new Diagnostic("INVALID_CONTENT", "ADF root must contain array field 'content'.", null));
    }

    return List.copyOf(issues);
  }

  // Accept the version as a JSON number or a numeric string ("1"); anything else yields null.
  private static @Nullable Integer versionOf(JsonNode versionNode) {
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
