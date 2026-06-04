package dev.nthings.adf4j.internal.analyze;

import java.util.ArrayList;
import java.util.List;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.internal.parser.AdfAstParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class AdfHeadingCollectorTests {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final AdfAstParser PARSER = new AdfAstParser(MAPPER);

  private static final List<Arguments> normalized_heading_content =
      List.of(
          argumentSet(
              "emphasis marks are preserved on the heading render path",
              new NormalizedHeadingCase(
                  """
                  [
                    {
                      "type": "text",
                      "text": "Code Heading",
                      "marks": [{"type": "strong"}, {"type": "code"}, {"type": "em"}]
                    }
                  ]
                  """,
                  List.of(new ExpectedInlineNode("Code Heading", List.of("strong", "code", "em"))))),
          argumentSet(
              "plain text nodes pass through without synthetic marks",
              new NormalizedHeadingCase(
                  """
                  [
                    {
                      "type": "text",
                      "text": "Plain Heading"
                    }
                  ]
                  """,
                  List.of(new ExpectedInlineNode("Plain Heading", List.of())))),
          argumentSet(
              "heading-only hard breaks and anchor macros do not become heading text",
              new NormalizedHeadingCase(
                  """
                  [
                    {
                      "type": "hardBreak"
                    },
                    {
                      "type": "inlineExtension",
                      "attrs": {
                        "extensionType": "com.atlassian.confluence.macro.core",
                        "extensionKey": "anchor",
                        "parameters": {
                          "macroParams": {
                            "": {
                              "value": "custom-section"
                            }
                          }
                        }
                      }
                    },
                    {
                      "type": "text",
                      "text": "Section A"
                    },
                    {
                      "type": "hardBreak"
                    }
                  ]
                  """,
                  List.of(new ExpectedInlineNode("Section A", List.of())))));
  private static final List<Arguments> heading_outline_documents =
      List.of(
          argumentSet(
              "single heading gets a generated slug anchor",
              new HeadingOutlineCase(
                  adfWithHeading(2, "Getting Started"),
                  List.of(new ExpectedHeading(2, "Getting Started", "getting-started")))),
          argumentSet(
              "repeated headings get stable generated anchor suffixes",
              new HeadingOutlineCase(
                  """
                  {
                    "type": "doc",
                    "version": 1,
                    "content": [
                      {
                        "type": "heading",
                        "attrs": {"level": 2},
                        "content": [{"type": "text", "text": "Section"}]
                      },
                      {
                        "type": "heading",
                        "attrs": {"level": 2},
                        "content": [{"type": "text", "text": "Section"}]
                      },
                      {
                        "type": "heading",
                        "attrs": {"level": 2},
                        "content": [{"type": "text", "text": "Section"}]
                      }
                    ]
                  }
                  """,
                  List.of(
                      new ExpectedHeading(2, "Section", "section"),
                      new ExpectedHeading(2, "Section", "section-1"),
                      new ExpectedHeading(2, "Section", "section-2")))),
          argumentSet(
              "heading text is extracted after inline normalization",
              new HeadingOutlineCase(
                  """
                  {
                    "type": "doc",
                    "version": 1,
                    "content": [
                      {
                        "type": "heading",
                        "attrs": {"level": 2},
                        "content": [
                          {
                            "type": "text",
                            "text": "Bold Title",
                            "marks": [{"type": "strong"}]
                          }
                        ]
                      }
                    ]
                  }
                  """,
                  List.of(new ExpectedHeading(2, "Bold Title", "bold-title")))),
          argumentSet(
              "explicit anchor macros override generated slug anchors",
              new HeadingOutlineCase(
                  """
                  {
                    "type": "doc",
                    "version": 1,
                    "content": [
                      {
                        "type": "heading",
                        "attrs": {"level": 2},
                        "content": [
                          {
                            "type": "inlineExtension",
                            "attrs": {
                              "extensionType": "com.atlassian.confluence.macro.core",
                              "extensionKey": "anchor",
                              "parameters": {
                                "macroParams": {
                                  "": {
                                    "value": "custom-section"
                                  }
                                }
                              }
                            }
                          },
                          {
                            "type": "text",
                            "text": "Section A"
                          }
                        ]
                      }
                    ]
                  }
                  """,
                  List.of(new ExpectedHeading(2, "Section A", "custom-section")))));

  private final AdfHeadingCollector collector = new AdfHeadingCollector();

  record NormalizedHeadingCase(String contentJson, List<ExpectedInlineNode> expectedNodes) {}

  record ExpectedInlineNode(String text, List<String> markTypes) {}

  record HeadingOutlineCase(String adfJson, List<ExpectedHeading> expectedHeadings) {}

  record ExpectedHeading(int level, String text, String anchor) {}

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("normalized_heading_content")
  void normalized_heading_nodes_keep_only_heading_text_that_affects_rendering(
      NormalizedHeadingCase input) throws Exception {
    var inlineList = PARSER.parseInlines(MAPPER.readTree(input.contentJson()));
    var nodes = HeadingContent.normalizedHeadingNodes(inlineList);

    assertThat(nodes)
        .extracting(AdfHeadingCollectorTests::textOf, AdfHeadingCollectorTests::markTypes)
        .containsExactlyElementsOf(
            input.expectedNodes().stream()
                .map(expected -> tuple(expected.text(), expected.markTypes()))
                .toList());
  }

  @Test
  void normalized_heading_nodes_preserve_emphasis_marks() throws Exception {
    var inlineList = PARSER.parseInlines(
        MAPPER.readTree(
            """
            [
              {
                "type": "text",
                "text": "Bold Heading",
                "marks": [{"type": "strong"}]
              }
            ]
            """));

    var normalized = HeadingContent.normalizedHeadingNodes(inlineList);

    assertThat(inlineList).hasSize(1);
    assertThat(inlineList.getFirst()).isInstanceOf(Text.class);
    assertThat(((Text) inlineList.getFirst()).marks()).hasSize(1);
    assertThat(normalized).hasSize(1);
    assertThat(((Text) normalized.getFirst()).marks()).hasSize(1);
  }

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("heading_outline_documents")
  void collect_builds_heading_outline_from_rendered_heading_text(HeadingOutlineCase input)
      throws Exception {
    var document = PARSER.parseDocument(MAPPER.readTree(input.adfJson()));
    var outline = collector.collect(document);

    assertThat(outline.headings())
        .extracting("level", "text", "anchor")
        .containsExactlyElementsOf(
            input.expectedHeadings().stream()
                .map(expected -> tuple(expected.level(), expected.text(), expected.anchor()))
                .toList());
  }

  @Test
  void collect_returns_empty_outline_for_null_adf_input() {
    var outline = collector.collect(null);

    assertThat(outline.headings()).isEmpty();
  }

  @Test
  void headings_without_any_toc_macro_are_not_toc_referenced() throws Exception {
    var document = PARSER.parseDocument(MAPPER.readTree(adfWithHeading(2, "Getting Started")));
    var outline = collector.collect(document);

    assertThat(tocReferencedFlags(document, outline)).containsExactly(false);
  }

  @Test
  void a_toc_macro_marks_every_heading_in_its_level_range_as_toc_referenced() throws Exception {
    var document = PARSER.parseDocument(MAPPER.readTree(TOC_WITH_HEADINGS));
    var outline = collector.collect(document);

    // The toc covers levels 1..2, so both the level-1 and level-2 headings are referenced.
    assertThat(tocReferencedFlags(document, outline)).containsExactly(true, true);
  }

  @Test
  void a_toc_macro_does_not_reference_headings_outside_its_level_range() throws Exception {
    var document = PARSER.parseDocument(MAPPER.readTree(TOC_LEVEL_1_ONLY));
    var outline = collector.collect(document);

    // minLevel=maxLevel=1, so only the level-1 heading is referenced.
    assertThat(tocReferencedFlags(document, outline)).containsExactly(true, false);
  }

  private static List<Boolean> tocReferencedFlags(AdfDocument document, HeadingOutline outline) {
    return document.content().stream()
        .filter(Heading.class::isInstance)
        .map(Heading.class::cast)
        .map(outline::isTocReferenced)
        .toList();
  }

  private static final String TOC_WITH_HEADINGS =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "toc",
              "parameters": {"macroParams": {"minLevel": {"value": "1"}, "maxLevel": {"value": "2"}}}
            }
          },
          {"type": "heading", "attrs": {"level": 1}, "content": [{"type": "text", "text": "Top"}]},
          {"type": "heading", "attrs": {"level": 2}, "content": [{"type": "text", "text": "Sub"}]}
        ]
      }
      """;

  private static final String TOC_LEVEL_1_ONLY =
      """
      {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "extension",
            "attrs": {
              "extensionType": "com.atlassian.confluence.macro.core",
              "extensionKey": "toc",
              "parameters": {"macroParams": {"minLevel": {"value": "1"}, "maxLevel": {"value": "1"}}}
            }
          },
          {"type": "heading", "attrs": {"level": 1}, "content": [{"type": "text", "text": "Top"}]},
          {"type": "heading", "attrs": {"level": 2}, "content": [{"type": "text", "text": "Sub"}]}
        ]
      }
      """;

  private static String adfWithHeading(int level, String text) {
    return
        """
        {
          "type": "doc",
          "version": 1,
          "content": [
            {
              "type": "heading",
              "attrs": {"level": %d},
              "content": [{"type": "text", "text": "%s"}]
            }
          ]
        }
        """
            .formatted(level, text);
  }

  private static String textOf(AdfInline node) {
    return node instanceof Text text ? text.text() : "";
  }

  private static List<String> markTypes(AdfInline node) {
    if (!(node instanceof Text text)) {
      return List.of();
    }
    var types = new ArrayList<String>();
    text.marks().forEach(mark -> types.add(mark.type()));
    return List.copyOf(types);
  }
}
