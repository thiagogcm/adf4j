package dev.nthings.adf4j.options;

import java.util.List;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.AttachmentReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentsMacroOptionsTests {

  private static final String ATTACHMENTS_MACRO =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "attachments",
              "parameters": { "macroParams": {} }
            }
          }
        ]
      }
      """;

  private static final List<AttachmentReference> INVENTORY = List.of(
      new AttachmentReference("file-1", "spec.pdf", "application/pdf"),
      new AttachmentReference("file-2", "diagram.png", "image/png"));

  private static MarkdownOptions withInventory(List<AttachmentReference> references) {
    return MarkdownOptions.defaults().withConfluenceContext(
        ConfluenceRenderContext.empty().withAttachmentReferences(references));
  }

  @Test
  void without_an_attachment_context_the_macro_keeps_its_placeholder() {
    var result = AdfToMarkdown.create().convert(ATTACHMENTS_MACRO);

    assertThat(result.body())
        .contains("\\[Extension: com.atlassian.confluence.macro.core/attachments\\]");
    assertThat(result.diagnostics()).anyMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
  }

  @Test
  void a_supplied_inventory_expands_to_a_list_of_attachment_links() {
    var result = AdfToMarkdown.with(withInventory(INVENTORY)).convert(ATTACHMENTS_MACRO);

    assertThat(result.body()).isEqualTo(
        """
        - [spec.pdf](attachment:file-1)
        - [diagram.png](attachment:file-2)""");
    assertThat(result.diagnostics()).noneMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
  }

  @Test
  void an_attachment_resolver_supplies_the_link_destinations() {
    var options = withInventory(INVENTORY)
        .withAttachmentResolver(reference -> "files/" + reference.title());

    var result = AdfToMarkdown.with(options).convert(ATTACHMENTS_MACRO);

    assertThat(result.body()).isEqualTo(
        """
        - [spec.pdf](files/spec.pdf)
        - [diagram.png](files/diagram.png)""");
  }

  @Test
  void an_authoritative_empty_inventory_renders_as_nothing() {
    var result = AdfToMarkdown.with(withInventory(List.of())).convert(ATTACHMENTS_MACRO);

    assertThat(result.body()).isEmpty();
    assertThat(result.diagnostics()).noneMatch(d -> "UNSUPPORTED_MACRO".equals(d.code()));
  }

  @Test
  void a_supplied_inventory_is_referenced_in_metadata() {
    var metadata = AdfToMarkdown.with(withInventory(INVENTORY)).analyze(ATTACHMENTS_MACRO);

    assertThat(metadata.referencedFileIds()).containsExactly("file-1", "file-2");
  }

  @Test
  void without_a_context_the_macro_contributes_no_attachment_refs() {
    var metadata = AdfToMarkdown.create().analyze(ATTACHMENTS_MACRO);

    assertThat(metadata.attachmentRefs()).isEmpty();
  }
}
