package dev.nthings.adf4j;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ValidationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/// Validates against the bundled ADF schema. The string overload checks the original JSON; the
/// document overload checks its serialized form.
public final class AdfValidation {
  private static final Schema SCHEMA = schema();

  private AdfValidation() {}

  /// The input's parse errors, if its JSON or root is unusable; otherwise its schema violations.
  public static ValidationResult validate(String json) {
    var tree = AdfJson.PARSER.readTree(json);
    var root = tree.root();
    return root == null ? new ValidationResult(tree.diagnostics()) : schemaViolations(root);
  }

  public static ValidationResult validate(AdfDocument document) {
    return schemaViolations(AdfJson.WRITER.tree(Objects.requireNonNull(document, "document")));
  }

  private static ValidationResult schemaViolations(JsonNode root) {
    var diagnostics =
        SCHEMA.validate(root).stream()
            .map(
                error ->
                    new Diagnostic(Diagnostic.Code.SCHEMA_VIOLATION, error.getMessage())
                        .at(pointer(error.getInstanceLocation())))
            .distinct()
            .toList();
    return new ValidationResult(diagnostics);
  }

  /// Returns the document, or throws AdfException on schema violations.
  public static AdfDocument requireValid(AdfDocument document) {
    validate(document).requireSuccess();
    return document;
  }

  private static String pointer(NodePath path) {
    var result = new StringBuilder();
    for (var i = 0; i < path.getNameCount(); i++) {
      result.append('/').append(Strings.pointerToken(String.valueOf(path.getElement(i))));
    }
    return result.toString();
  }

  private static Schema schema() {
    try (var stream =
        Objects.requireNonNull(AdfValidation.class.getResourceAsStream("adf-schema.json"))) {
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4)
          .getSchema(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException error) {
      throw new UncheckedIOException(error);
    }
  }
}
