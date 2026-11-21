package dev.nthings.adf4j.internal.engine;

import static dev.nthings.adf4j.ast.Adf.doc;
import static dev.nthings.adf4j.ast.Adf.heading;
import static dev.nthings.adf4j.ast.Adf.paragraph;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.internal.render.AdfDocumentRenderer;
import dev.nthings.adf4j.metadata.ContentMetadata;
import dev.nthings.adf4j.options.DocumentContext;
import dev.nthings.adf4j.options.ResourceResolvers;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.ConversionEffect;
import dev.nthings.adf4j.result.ConversionResult;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.ParseResult;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/// Verifies that `AdfPipeline` and `AdfJson.writeResult` work with non-Markdown renderer backends.
class AdfPipelineTests {

  private record CustomResult(
      String body,
      ContentMetadata metadata,
      List<Diagnostic> diagnostics,
      List<UnresolvedResource> unresolved,
      List<ConversionEffect> effects)
      implements ConversionResult {}

  @Test
  void custom_renderer_shares_pipeline_analysis_and_json_serialization() {
    var pipeline = AdfPipeline.createDefault();
    AdfDocumentRenderer htmlStub =
        (document, env) -> {
          var firstHeading = env.headingOutline().headings().getFirst();
          return "<h%d id=\"%s\">%s</h%d>"
              .formatted(
                  firstHeading.level(),
                  firstHeading.anchor(),
                  firstHeading.text(),
                  firstHeading.level());
        };

    var document = doc(heading(2, "Overview"), paragraph("Body"));
    var result =
        pipeline.render(
            new ParseResult(document, List.of()),
            DocumentContext.defaults(),
            UnknownNodePolicy.PLACEHOLDER,
            ResourceResolvers.defaults(),
            htmlStub,
            CustomResult::new);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.requireBody()).isEqualTo("<h2 id=\"overview\">Overview</h2>");
    assertThat(result.metadata().outline()).hasSize(1);

    var json = JsonMapper.builder().build().readTree(AdfJson.writeResult(result));
    assertThat(json.get("ok").asBoolean()).isTrue();
    assertThat(json.get("body").asString()).isEqualTo("<h2 id=\"overview\">Overview</h2>");
    assertThat(json.get("metadata").get("outline").get(0).get("anchor").asString())
        .isEqualTo("overview");
  }
}
