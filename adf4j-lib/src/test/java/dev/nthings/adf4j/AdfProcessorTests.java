package dev.nthings.adf4j;

import java.util.List;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.parser.AdfAstParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class AdfProcessorTests {

  private static final AdfProcessor PROCESSOR = new AdfProcessor();
  private static final String MINIMAL_ADF =
      """
      {
        "type": "doc",
        "version": 1,
        "content": []
      }
      """;
  private static final AdfDocument MINIMAL_ADF_DOCUMENT = parseDocument(MINIMAL_ADF);
  private static final List<Arguments> option_aware_api_calls =
      List.of(
          argumentSet(
              "renderStorageDocument(String, RenderOptions)",
              new NullOptionCall(() -> PROCESSOR.renderStorageDocument(MINIMAL_ADF, null))),
          argumentSet(
              "renderStorageMarkdown(String, RenderOptions)",
              new NullOptionCall(() -> PROCESSOR.renderStorageMarkdown(MINIMAL_ADF, null))),
          argumentSet(
              "renderStorageMarkdown(AdfDocument, RenderOptions)",
              new NullOptionCall(() -> PROCESSOR.renderStorageMarkdown(MINIMAL_ADF_DOCUMENT, null))),
          argumentSet(
              "extractContentMetadata(String, RenderOptions)",
              new NullOptionCall(() -> PROCESSOR.extractContentMetadata(MINIMAL_ADF, null))),
          argumentSet(
              "extractContentMetadata(AdfDocument, RenderOptions)",
              new NullOptionCall(() -> PROCESSOR.extractContentMetadata(MINIMAL_ADF_DOCUMENT, null))),
          argumentSet(
              "renderPresentationMarkdown(String, RenderOptions)",
              new NullOptionCall(() -> PROCESSOR.renderPresentationMarkdown(MINIMAL_ADF, null))),
          argumentSet(
              "renderPresentationMarkdown(AdfDocument, RenderOptions)",
              new NullOptionCall(
                  () -> PROCESSOR.renderPresentationMarkdown(MINIMAL_ADF_DOCUMENT, null))),
          argumentSet(
              "renderPresentationHtml(String, RenderOptions)",
              new NullOptionCall(() -> PROCESSOR.renderPresentationHtml(MINIMAL_ADF, null))));

  record NullOptionCall(Runnable runnable) {
    void invoke() {
      runnable.run();
    }
  }

  @Test
  void default_construction_exposes_the_public_parse_and_render_workflow() {
    var markdown = PROCESSOR.renderStorageMarkdown(
        """
            {
              "type": "doc",
              "version": 1,
              "content": [
                {
                  "type": "paragraph",
                  "content": [
                    {
                      "type": "text",
                      "text": "Hello, ADF"
                    }
                  ]
                }
              ]
            }
            """);

    assertThat(markdown).isEqualTo("Hello, ADF");
    assertThat(PROCESSOR.parse("{\"type\":\"doc\",\"version\":1,\"content\":[]}").validAdfRoot())
        .isTrue();
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("option_aware_api_calls")
  void option_aware_apis_reject_null_render_options(NullOptionCall apiCall) {
    assertThatNullPointerException()
        .isThrownBy(apiCall::invoke)
        .withMessage("options");
  }

  private static AdfDocument parseDocument(String rawJson) {
    try {
      var mapper = JsonMapper.builder().build();
      var parser = new AdfAstParser(mapper);
      return parser.parseDocument(mapper.readTree(rawJson));
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid minimal ADF test payload", exception);
    }
  }
}
