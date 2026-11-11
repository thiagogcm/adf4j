package dev.nthings.adf4j.internal.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdfDocumentAnalyzerTests {

  private static final AttachmentInventory NO_ATTACHMENTS = AttachmentInventory.from(null);
  private static final AdfDocumentAnalyzer ANALYZER = AdfDocumentAnalyzer.createDefault();

  private static final String DOCUMENT =
      """
      {"type":"doc","version":1,"content":[
        {"type":"mysteryBlock"},
        {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Title"}]},
        {"type":"paragraph","content":[
          {"type":"text","text":"hi","marks":[{"type":"futureMark"}]}
        ]}
      ]}
      """;

  @Test
  void facts_and_analysis_share_the_same_reference_collectors() {
    var document = AdfJson.parse(DOCUMENT).document();

    var facts = ANALYZER.facts(document, UnknownNodePolicy.SKIP, NO_ATTACHMENTS);
    var analysis = ANALYZER.analyze(document, UnknownNodePolicy.SKIP, NO_ATTACHMENTS);

    assertThat(facts.metadata()).isEqualTo(analysis.facts().metadata());
    assertThat(facts.outline().headings())
        .containsExactlyElementsOf(analysis.facts().outline().headings());
    assertThat(facts.outline().referenceAt("/content/1"))
        .isEqualTo(new HeadingReference(2, "Title", "title"));
  }

  @Test
  void analysis_keeps_the_lossiness_messages_and_paths() {
    var document = AdfJson.parse(DOCUMENT).document();

    var analysis = ANALYZER.analyze(document, UnknownNodePolicy.SKIP, NO_ATTACHMENTS);

    assertThat(analysis.diagnostics())
        .extracting(Diagnostic::code, Diagnostic::message, Diagnostic::paths)
        .containsExactly(
            tuple(
                Diagnostic.Code.UNKNOWN_NODE_SKIPPED,
                "1 unsupported node(s) dropped from the output.",
                List.of("/content/0")),
            tuple(
                Diagnostic.Code.UNKNOWN_MARK_DROPPED,
                "1 unsupported mark(s) dropped from the output.",
                List.of("/content/2/content/0/marks/0")));
  }

  @Test
  void facts_follow_the_unknown_node_policy() {
    var document =
        AdfJson.parse(
                """
                {"type":"doc","version":1,"content":[
                  {"type":"heading","attrs":{"level":2},"content":[
                    {"type":"futureInline"}
                  ]}
                ]}
                """)
            .document();

    assertThat(
            ANALYZER
                .facts(document, UnknownNodePolicy.PLACEHOLDER, NO_ATTACHMENTS)
                .outline()
                .headings())
        .extracting("text")
        .containsExactly("[Unsupported inline: futureInline]");
    assertThat(
            ANALYZER.facts(document, UnknownNodePolicy.SKIP, NO_ATTACHMENTS).outline().headings())
        .isEmpty();
  }

  @Test
  void null_documents_have_empty_facts_and_analysis() {
    assertThat(ANALYZER.facts(null, UnknownNodePolicy.PLACEHOLDER, NO_ATTACHMENTS))
        .isEqualTo(DocumentFacts.empty());
    assertThat(ANALYZER.analyze(null, UnknownNodePolicy.PLACEHOLDER, NO_ATTACHMENTS))
        .isEqualTo(DocumentAnalysis.empty());
  }
}
