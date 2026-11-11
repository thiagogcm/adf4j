package dev.nthings.adf4j;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ValidationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/// Checks the original JSON against the bundled ADF schema. Parsing remains tolerant.
public final class AdfValidation {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Schema SCHEMA = schema();

  private AdfValidation() {}

  public static ValidationResult validate(String json) {
    var parsed = AdfJson.parse(json);
    if (!parsed.isSuccess()) return new ValidationResult(parsed.diagnostics());
    var diagnostics =
        SCHEMA.validate(JSON.readTree(json)).stream()
            .map(
                error ->
                    new Diagnostic(Diagnostic.Code.SCHEMA_VIOLATION, error.getMessage())
                        .at(pointer(error.getInstanceLocation())))
            .distinct()
            .toList();
    return new ValidationResult(diagnostics);
  }

  public static ValidationResult validate(AdfDocument document) {
    return validate(AdfJson.write(document));
  }

  public static AdfDocument requireValid(AdfDocument document) {
    validate(document).requireSuccess();
    return document;
  }

  private static String pointer(NodePath path) {
    var result = new StringBuilder();
    for (int i = 0; i < path.getNameCount(); i++)
      result
          .append('/')
          .append(String.valueOf(path.getElement(i)).replace("~", "~0").replace("/", "~1"));
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
