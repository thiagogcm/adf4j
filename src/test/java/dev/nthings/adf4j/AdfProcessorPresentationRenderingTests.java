package dev.nthings.adf4j;

import java.util.List;
import java.util.Optional;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdfProcessorPresentationRenderingTests {

    private final AdfTestSupport testSupport = AdfTestSupport.create();
    private final AdfProcessor processor = testSupport.processor();

    @Test
    void render_presentation_html_matches_the_manual_installation_regression_case()
            throws Exception {
        var html = processor.renderPresentationHtml(
                testSupport.caseInput("manual-instalacao"),
                optionsForPage("Installation Fixture"));

        assertThat(html)
                .isEqualToNormalizingNewlines(
                        testSupport.caseOutput("manual-instalacao", ".presentation.html"));
    }

    @Test
    void render_presentation_html_rewrites_internal_page_links_and_resolves_readable_labels()
            throws Exception {
        var rawPayload = testSupport.caseInput("internal-page-links");
        var context = ConfluenceRenderContext.forPage("Resolver Fixture")
                .withPageId("page-current")
                .withPageLinkResolver(
                        (currentPageId, targetPageId) -> Optional.of("/pages/" + targetPageId))
                .withPageTitleResolver(
                        pageNodeId -> Optional.of(
                                switch (pageNodeId) {
                                    case "12345" -> "Runbook Page";
                                    case "54321" -> "Draft Guide";
                                    default -> "Page " + pageNodeId;
                                }));
        var options = RenderOptions.defaults().withContext(context);

        var html = processor.renderPresentationHtml(rawPayload, options);

        assertThat(html)
                .contains("<a href=\"/pages/12345\">Runbook Page</a>")
                .contains("<a href=\"/pages/54321\">Draft Guide</a>");
    }

    @Test
    void render_presentation_html_keeps_original_external_confluence_url_when_resolver_declines_target() {
        var rawPayload = """
                {
                    "type": "doc",
                    "version": 1,
                    "content": [
                        {
                            "type": "paragraph",
                            "content": [
                                {
                                    "type": "text",
                                    "text": "Documentação funcional",
                                    "marks": [
                                        {
                                            "type": "link",
                                            "attrs": {
                                                "href": "https://openfinancebrasil.atlassian.net/wiki/spaces/DraftOF/pages/258277703/Reporte#Campos-especiais"
                                            }
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
                """;

        var html = processor.renderPresentationHtml(
                rawPayload,
                RenderOptions.defaults().withContext(
                        ConfluenceRenderContext.forPage("External Link Fixture")
                                .withPageId("37945515")
                                .withPageLinkResolver((currentPageId, targetPageId) -> Optional.empty())));

        assertThat(html)
                .contains(
                        "href=\"https://openfinancebrasil.atlassian.net/wiki/spaces/DraftOF/pages/258277703/Reporte#Campos-especiais\"")
                .contains(">Documentação funcional</a>");
    }

    @Test
    void render_presentation_html_aligns_explicit_anchor_macros_with_toc_fragment_links()
            throws Exception {
        var rawPayload = testSupport.caseInput("anchor-macros");

        var html = processor.renderPresentationHtml(
                rawPayload, optionsForPage("Anchor Fixture"));

        assertThat(html)
                .contains("href=\"#custom-section\"")
                .contains("<h2 id=\"custom-section\">Section A</h2>");
    }

    @Test
    void render_presentation_html_falls_back_to_sanitized_markdown_for_malformed_raw_input() {
        var invalidJson = "# Heading\n\nParagraph";
        var invalidRoot = "{\"type\":\"paragraph\",\"version\":1,\"content\":[]}";

        assertThat(processor.renderPresentationHtml(invalidJson, RenderOptions.defaults()))
                .contains("Heading")
                .contains("Paragraph");
        assertThat(processor.renderPresentationHtml(invalidRoot, RenderOptions.defaults()))
                .contains("paragraph")
                .contains("version")
                .contains("content");
    }

    @Test
    void render_presentation_html_markdown_fallback_preserves_supported_href_schemes_and_strips_unsafe_html() {
        var markdown = """
                [Page](/pages/42)
                [Fragment](#details)
                [Attachment](attachment:file-1)

                <a href="javascript:alert('x')">bad</a>
                <script>alert("x")</script>
                """;

        var html = processor.renderPresentationHtml(markdown, RenderOptions.defaults());

        assertThat(html)
                .contains("href=\"/pages/42\"")
                .contains("href=\"#details\"")
                .contains("href=\"attachment:file-1\"")
                .contains(">bad</a>")
                .doesNotContain("javascript:alert")
                .doesNotContain("<script");
    }

    @Test
    void render_presentation_html_expands_db_derived_children_macros_with_nested_descendants()
            throws Exception {
        var options = RenderOptions.defaults()
                .withContext(
                        ConfluenceRenderContext.forPage("Reporting Specifications")
                                .withChildPages(
                                        List.of(
                                                new ConfluenceRenderContext.ChildPage(
                                                        "child-1",
                                                        "Alpha Child",
                                                        List.of(
                                                                new ConfluenceRenderContext.ChildPage(
                                                                        "grandchild-1", "Nested Child"))),
                                                new ConfluenceRenderContext.ChildPage("child-2", "Beta Child"))));

        var html = processor.renderPresentationHtml(
                testSupport.caseInput("especificacoes-reporte-children"), options);

        assertThat(html)
                .contains("src=\"media:children-reporting-icon\"")
                .contains("alt=\"Reporting icon\"")
                .contains("height=\"22\"")
                .contains("<a href=\"/pages/child-1\">Alpha Child</a>")
                .contains("<a href=\"/pages/grandchild-1\">Nested Child</a>")
                .contains("<a href=\"/pages/child-2\">Beta Child</a>");
    }

    @Test
    void render_presentation_html_resolves_db_derived_viewpdf_cases_with_attachment_context()
            throws Exception {
        var options = RenderOptions.defaults()
                .withContext(
                        ConfluenceRenderContext.forPage("Participant Guide")
                                .withAttachmentReferences(
                                        List.of(
                                                new AttachmentReference(
                                                        "file-pdf-123",
                                                        "Open_Finance_cadastro_diretorio_passo_a_passo.pdf",
                                                        "application/pdf"))));

        var html = processor.renderPresentationHtml(
                testSupport.caseInput("lista-participantes-viewpdf"), options);

        assertThat(html)
                .contains("href=\"https://data.directory.openbankingbrasil.org.br/participants\"")
                .contains(
                        "href=\"https://github.com/OpenBanking-Brasil/specs-directory/blob/main/swagger_participants.yaml\"")
                .contains("href=\"attachment:file-pdf-123\"")
                .contains("PDF: Open_Finance_cadastro_diretorio_passo_a_passo.pdf");
    }

    private static RenderOptions optionsForPage(String pageTitle) {
        return RenderOptions.defaults().withContext(ConfluenceRenderContext.forPage(pageTitle));
    }
}
