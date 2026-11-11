package dev.nthings.adf4j.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.testing.Outcome;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

/// What a conversion reports about itself: diagnostics (with their severity, which decides
/// `isSuccess` and `hasWarnings`), effects on the output, and unresolved dependencies. Each
/// scenario pins the complete outcome, so an unexpected extra report fails it.
class ReportingTests {

  private static final String MACROS = "com.atlassian.confluence.macro.core";
  private static final String HI = paragraph(text("Hi"));
  private static final String UNKNOWN_BLOCK = "{\"type\":\"mysteryBlock\"}";
  private static final String UNKNOWN_MARK =
      paragraph("{\"type\":\"text\",\"text\":\"hi\",\"marks\":[{\"type\":\"futureMark\"}]}");
  private static final String ROOTED_PAGETREE =
      macro("extension", "pagetree", "{\"root\":{\"value\":\"Docs Home\"}}");
  private static final String PAGE_LINK =
      paragraph(
          "{\"type\":\"text\",\"text\":\"spec\",\"marks\":[{\"type\":\"link\",\"attrs\":"
              + "{\"href\":\"https://x.example/wiki/spaces/S/pages/12345/Spec\"}}]}");

  record Scenario(String json, ConversionOptions options, String outcome) {}

  private static final List<Arguments> diagnostics =
      List.of(
          scenario("a clean document reports nothing", doc(HI), "Hi"),
          scenario(
              "an unsupported version is a warning on the root",
              "{\"type\":\"doc\",\"version\":2,\"content\":[" + HI + "]}",
              """
              Hi
              == diagnostics
              WARNING UNSUPPORTED_VERSION [/version]"""),
          scenario(
              "unreadable JSON is an error with an empty body",
              "{",
              """

              == diagnostics
              ERROR INVALID_JSON []"""),
          scenario(
              "an unknown node becomes a placeholder by default",
              doc(HI, UNKNOWN_BLOCK),
              """
              Hi

              \\[Unsupported: mysteryBlock\\]
              == diagnostics
              WARNING UNKNOWN_NODE_PLACEHOLDER [/content/1]
              == effects
              PLACEHOLDER /content/1"""),
          scenario(
              "a skipped unknown node is dropped content",
              doc(HI, UNKNOWN_BLOCK),
              policy(UnknownNodePolicy.SKIP),
              """
              Hi
              == diagnostics
              WARNING UNKNOWN_NODE_SKIPPED [/content/1]
              == effects
              CONTENT_DROPPED /content/1"""),
          scenario(
              "a preserved unknown node is only noted",
              doc(HI, UNKNOWN_BLOCK),
              policy(UnknownNodePolicy.PRESERVE_RAW),
              """
              Hi

              ```json
              {"type":"mysteryBlock"}
              ```
              == diagnostics
              INFO UNKNOWN_NODE_PRESERVED [/content/1]"""),
          scenario(
              "a rejected unknown node fails the conversion",
              doc(HI, UNKNOWN_BLOCK),
              policy(UnknownNodePolicy.FAIL),
              """

              == diagnostics
              ERROR UNKNOWN_NODE_REJECTED [/content/1]"""),
          scenario(
              "an unknown mark is dropped while its text renders",
              doc(UNKNOWN_MARK),
              """
              hi
              == diagnostics
              WARNING UNKNOWN_MARK_DROPPED [/content/0/content/0/marks/0]
              == effects
              CONTENT_DROPPED /content/0/content/0/marks/0"""),
          scenario(
              "preserving raw nodes still reports dropped marks",
              doc(UNKNOWN_BLOCK, UNKNOWN_MARK),
              policy(UnknownNodePolicy.PRESERVE_RAW),
              """
              ```json
              {"type":"mysteryBlock"}
              ```

              hi
              == diagnostics
              INFO UNKNOWN_NODE_PRESERVED [/content/0]
              WARNING UNKNOWN_MARK_DROPPED [/content/1/content/0/marks/0]
              == effects
              CONTENT_DROPPED /content/1/content/0/marks/0"""),
          scenario(
              "a blank-typed unknown block and inline still render their placeholders",
              doc("{\"type\":\"\"}", paragraph("{\"type\":\"\"}")),
              """
              \\[Unsupported\\]

              \\[Unsupported inline\\]
              == diagnostics
              WARNING UNKNOWN_NODE_PLACEHOLDER [/content/0, /content/1/content/0]
              == effects
              PLACEHOLDER /content/0
              PLACEHOLDER /content/1/content/0"""),
          scenario(
              "unsupported macros aggregate into one diagnostic with every occurrence",
              doc(
                  macro("extension", "widget", "{}"),
                  paragraph(text("See "), macro("inlineExtension", "widget", "{}")),
                  macro("extension", "detailssummary", "{}")),
              """
              \\[Extension: com.atlassian.confluence.macro.core/widget\\]

              See \\[Extension: com.atlassian.confluence.macro.core/widget\\]

              \\[Extension: com.atlassian.confluence.macro.core/detailssummary\\]
              == diagnostics
              WARNING UNSUPPORTED_MACRO [/content/0, /content/1/content/1, /content/2]
              == effects
              PLACEHOLDER /content/0
              PLACEHOLDER /content/1/content/1
              PLACEHOLDER /content/2"""),
          scenario(
              "a macro with fallback text is not unsupported",
              doc(
                  "{\"type\":\"extension\",\"attrs\":{\"extensionType\":\""
                      + MACROS
                      + "\",\"extensionKey\":\"detailssummary\",\"text\":\"Static fallback\"}}"),
              "Static fallback"),
          scenario(
              "handled macros raise no diagnostics; a children tree without a resolver is unresolved",
              doc(
                  macro("extension", "children", "{}"),
                  macro("bodiedExtension", "excerpt", "{}", HI)),
              """
              {{children}}

              Hi
              == effects
              PLACEHOLDER /content/0
              == unresolved
              PAGE_TREE NO_ANSWER /content/0 id= fallback={{children}}"""),
          scenario(
              "a custom renderer takes over an unsupported macro",
              doc(macro("extension", "detailssummary", "{}")),
              ConversionOptions.defaults()
                  .withExtensionRenderers(List.of(node -> ExtensionOutput.replace("- a row"))),
              "- a row"));

  private static final List<Arguments> unresolved =
      List.of(
          scenario(
              "a page link without a resolver is kept as is",
              doc(PAGE_LINK),
              "[spec](https://x.example/wiki/spaces/S/pages/12345/Spec)"),
          scenario(
              "a declined page link rewrite keeps the original link",
              doc(PAGE_LINK),
              ConversionOptions.defaults().withPageLinkResolver(id -> null),
              "[spec](https://x.example/wiki/spaces/S/pages/12345/Spec)"),
          scenario(
              "a resolved page link is rewritten",
              doc(PAGE_LINK),
              ConversionOptions.defaults().withPageLinkResolver(id -> "pages/" + id + ".md"),
              "[spec](pages/12345.md)"),
          scenario(
              "a page tree without a resolver leaves its token",
              doc(ROOTED_PAGETREE),
              """
              {{pagetree:Docs Home}}
              == effects
              PLACEHOLDER /content/0
              == unresolved
              PAGE_TREE NO_ANSWER /content/0 id=Docs Home fallback={{pagetree:Docs Home}}"""),
          scenario(
              "a declined page tree leaves its token",
              doc(ROOTED_PAGETREE),
              ConversionOptions.defaults().withPageTreeResolver(request -> null),
              """
              {{pagetree:Docs Home}}
              == effects
              PLACEHOLDER /content/0
              == unresolved
              PAGE_TREE NO_ANSWER /content/0 id=Docs Home fallback={{pagetree:Docs Home}}"""),
          scenario(
              "an empty page tree is an answer",
              doc(ROOTED_PAGETREE),
              ConversionOptions.defaults().withPageTreeResolver(request -> List.of()),
              ""),
          scenario(
              "a failing page tree resolver is reported with its failure",
              doc(ROOTED_PAGETREE),
              ConversionOptions.defaults()
                  .withPageTreeResolver(
                      request -> {
                        throw new IllegalStateException("lookup failed");
                      }),
              """
              {{pagetree:Docs Home}}
              == diagnostics
              WARNING CALLBACK_FAILED [/content/0]
              == effects
              PLACEHOLDER /content/0
              == unresolved
              PAGE_TREE CALLBACK_FAILED /content/0 id=Docs Home fallback={{pagetree:Docs Home}}"""));

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource({"diagnostics", "unresolved"})
  void conversion_reports_exactly_what_happened(Scenario scenario) {
    var result = AdfToMarkdown.with(scenario.options()).convert(scenario.json());

    assertThat(Outcome.of(result)).isEqualTo(scenario.outcome());
  }

  private static Arguments scenario(String name, String json, String outcome) {
    return scenario(name, json, ConversionOptions.defaults(), outcome);
  }

  private static Arguments scenario(
      String name, String json, ConversionOptions options, String outcome) {
    return argumentSet(name, new Scenario(json, options, outcome));
  }

  private static ConversionOptions policy(UnknownNodePolicy policy) {
    return ConversionOptions.defaults().withUnknownNodePolicy(policy);
  }

  private static String doc(String... blocks) {
    return "{\"type\":\"doc\",\"version\":1,\"content\":[" + String.join(",", blocks) + "]}";
  }

  private static String paragraph(String... inlines) {
    return "{\"type\":\"paragraph\",\"content\":[" + String.join(",", inlines) + "]}";
  }

  private static String text(String value) {
    return "{\"type\":\"text\",\"text\":\"" + value + "\"}";
  }

  private static String macro(String type, String key, String params, String... content) {
    var body = content.length == 0 ? "" : ",\"content\":[" + String.join(",", content) + "]";
    return "{\"type\":\""
        + type
        + "\",\"attrs\":{\"extensionType\":\""
        + MACROS
        + "\",\"extensionKey\":\""
        + key
        + "\",\"parameters\":{\"macroParams\":"
        + params
        + "}}"
        + body
        + "}";
  }
}
