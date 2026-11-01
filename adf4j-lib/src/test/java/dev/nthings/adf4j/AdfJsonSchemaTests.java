package dev.nthings.adf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.nthings.adf4j.testing.AdfJsonComparison;
import dev.nthings.adf4j.testing.TestResources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/// Checks written documents against the published schema (`docs/spec/adf-schema.json`).
class AdfJsonSchemaTests {

  // Fixtures that model what products emit or tolerate beyond the published schema. Each still
  // round-trips; see adf/README.md.
  private static final Set<String> OFF_SCHEMA =
      Set.of(
          "inline/embed-card-as-inline-link.json", // embedCard inside a paragraph
          "inline/inline-block-card.json", // blockCard inside a paragraph
          "inline/media-inline.json", // mediaInline attrs beyond the schema
          "marks/border-omitted.json", // border mark without its required attrs
          "marks/breakout-fragment-dataconsumer-passthrough.json", // marks the node does not allow
          "nodes/code-block-strips-marks.json", // marked text inside a codeBlock
          "nodes/heading-with-inline-image.json", // mediaInline attrs beyond the schema
          "nodes/list-item-with-decision-sublist.json", // decisionList inside a listItem
          "nodes/media-single-image-png.json", // media attrs beyond the schema
          "nodes/multi-bodied-extension.json", // node missing from the schema
          "unknown-node-policy.json"); // deliberately unknown node type

  @Test
  void written_fixtures_conform_to_the_schema_unless_listed_as_off_schema() throws IOException {
    var schema = schema();
    var invalid = new TreeSet<String>();
    for (var file : TestResources.specFixtures()) {
      var written = AdfJson.write(AdfJson.parse(TestResources.read(file)).document());
      if (!schema.validate(AdfJsonComparison.MAPPER.readTree(written)).isEmpty()) {
        invalid.add(file.substring("adf/spec/".length()));
      }
    }

    assertAll(
        () -> assertThat(invalid).containsExactlyInAnyOrderElementsOf(OFF_SCHEMA),
        () -> assertThat(invalid).isNotEmpty());
  }

  private static Schema schema() throws IOException {
    var text = Files.readString(Path.of("../docs/spec/adf-schema.json"));
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4).getSchema(text);
  }
}
