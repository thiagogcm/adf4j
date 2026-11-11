package dev.nthings.adf4j;

import static dev.nthings.adf4j.ast.Adf.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nthings.adf4j.result.AdfException;
import org.junit.jupiter.api.Test;

class AuthoringWorkflowTests {
  @Test
  void editing_a_parsed_document_can_be_checked_before_submission() {
    var input = AdfJson.write(doc(panel(PanelType.INFO, paragraph("Read this"))));
    var parsed = AdfJson.parse(input).requireDocument();
    var edited = parsed.append(paragraph("Reviewed"));
    assertThat(AdfValidation.validate(edited).isSuccess()).isTrue();

    var invalid =
        doc(panel(PanelType.INFO, paragraph("Read this").withMarks(alignment(TextAlign.CENTER))));
    var json = AdfJson.write(invalid);
    // The parser must preserve imported data, even when a product would reject it.
    assertThat(AdfJson.write(AdfJson.parse(json).requireDocument())).isEqualTo(json);
    var validation = AdfValidation.validate(json);
    assertThat(validation.isSuccess()).isFalse();
    assertThat(validation.diagnostics())
        .anySatisfy(d -> assertThat(d.paths()).contains("/content/0/content/0/marks"));
    assertThatThrownBy(() -> AdfValidation.requireValid(invalid)).isInstanceOf(AdfException.class);
  }

  @Test
  void authoring_reports_invalid_heading_and_mark_inputs_at_the_call_site() {
    assertThatThrownBy(() -> heading(9, "Title"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("level");
    assertThatThrownBy(() -> text("code", code(), strong()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("code");
    assertThat(
            AdfValidation.validate(
                    doc(paragraph(text("code", code(), link("https://example.com")))))
                .isSuccess())
        .isTrue();
  }
}
