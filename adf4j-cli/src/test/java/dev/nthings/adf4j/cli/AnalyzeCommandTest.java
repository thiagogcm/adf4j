package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.MEDIA_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.UNKNOWN_NODE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.run;
import static dev.nthings.adf4j.cli.CliTestSupport.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.AdfJson;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AnalyzeCommandTest {

  // One excerpt, plus a reference for every section that a plain document leaves empty.
  private static final String RICH_DOC =
      """
      {"type":"doc","version":1,"content":[
        {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Two words"}]},
        {"type":"paragraph","content":[
          {"type":"text","text":"site","marks":[
            {"type":"link","attrs":{"href":"https://example.com/x"}}]},
          {"type":"mention","attrs":{"id":"u1","text":"@Ann"}}]},
        {"type":"bodiedExtension","attrs":{
          "extensionType":"com.atlassian.confluence.macro.core","extensionKey":"excerpt"},
          "content":[{"type":"paragraph","content":[{"type":"text","text":"Shared"}]}]},
        {"type":"extension","attrs":{
          "extensionType":"com.atlassian.confluence.macro.core","extensionKey":"pagetree",
          "parameters":{"macroParams":{"root":{"value":"Docs"},"depth":{"value":"2"}}}}},
        {"type":"extension","attrs":{
          "extensionType":"com.atlassian.confluence.macro.core","extensionKey":"excerpt-include",
          "parameters":{"macroParams":{"":{"value":"Source"},"name":{"value":"intro"}}}}},
        {"type":"mediaSingle","content":[
          {"type":"media","attrs":{"type":"file","id":"f1","collection":"col"}}]}
      ]}""";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void defaultJsonEmitsEverySectionInTableOrder() {
    var result = run(SIMPLE_DOC, "analyze");
    assertThat(result.exitCode()).isZero();
    assertThat(JSON.readTree(result.out()).path("metadata").propertyNames())
        .containsExactlyElementsOf(AdfJson.METADATA_SECTIONS);
    assertThat(result.out()).contains("\"text\" : \"Hello\"");
  }

  @Test
  void selectLimitsToChosenSectionsCommaSeparated() {
    var result = run(SIMPLE_DOC, "analyze", "--select", "outline,referencedFileIds");
    assertThat(JSON.readTree(result.out()).path("metadata").propertyNames())
        .containsExactly("referencedFileIds", "outline");
  }

  @Test
  void unknownSelectSectionIsAUsageError() {
    var result = run(SIMPLE_DOC, "analyze", "--select", "bogus");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("--select").contains("Allowed values").contains("pageIds");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void excerptsAreNamesOnly() {
    var result = run(RICH_DOC, "analyze", "--select", "excerpts");
    assertThat(result.exitCode()).isZero();
    var excerpts = JSON.readTree(result.out()).path("metadata").get("excerpts");
    assertThat(excerpts).hasSize(1);
    assertThat(excerpts.get(0).propertyNames()).containsExactly("name");
    assertThat(result.out()).doesNotContain("Shared");
  }

  @Test
  void renderingAndResolverFlagsAreGone() {
    for (var flag : new String[] {"--title", "--unknown-nodes", "--media-url", "--excerpt-map"}) {
      var result = run(SIMPLE_DOC, "analyze", flag, "x");
      assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
      assertThat(result.err()).contains(flag);
    }
  }

  @Test
  void attachmentsInventoryEnrichesReferences(@TempDir Path dir) {
    var inventory =
        write(
            dir,
            "attachments.json",
            "[{\"fileId\":\"FILE_ID\",\"title\":\"a.pdf\",\"downloadUrl\":\"https://x/a.pdf\"}]");
    var result = run(MEDIA_DOC, "analyze", "--attachments", inventory.toString());
    assertThat(result.exitCode()).isZero();
    var reference = JSON.readTree(result.out()).path("metadata").get("attachmentRefs").get(0);
    assertThat(reference.get("title").asString()).isEqualTo("a.pdf");
    assertThat(reference.get("downloadUrl").asString()).isEqualTo("https://x/a.pdf");
  }

  @Test
  void textFormatIsHumanReadable() {
    var result = run(SIMPLE_DOC, "analyze", "-f", "text", "--select", "outline,pageIds");
    assertThat(result.exitCode()).isZero();
    assertThat(result.out())
        .isEqualTo(
            """
            pageIds (0): none
            outline (1):
              level=1 text=Hello anchor=hello
            """);
  }

  @Test
  void textExposesEveryValueOfTheJson() {
    var json = JSON.readTree(run(RICH_DOC, "analyze").out()).path("metadata");
    var text = run(RICH_DOC, "analyze", "-f", "text").out();

    assertThat(json.propertyNames()).allSatisfy(section -> assertThat(text).contains(section));
    assertThat(json.valueStream().flatMap(AnalyzeCommandTest::leaves))
        .isNotEmpty()
        .allSatisfy(leaf -> assertThat(text).contains(leaf));
    assertThat(text)
        .contains("id=u1 text=@Ann")
        .contains("fileId=f1 title=null mediaType=null downloadUrl=null")
        .contains("macro=pagetree root=Docs depth=2 all=false parameters={")
        .contains("page=Source excerptName=intro parameters={")
        .contains("name=null");
  }

  private static Stream<String> leaves(JsonNode node) {
    return node.isContainer()
        ? node.valueStream().flatMap(AnalyzeCommandTest::leaves)
        : Stream.of(node.isNull() ? "null" : node.asString());
  }

  @Test
  void invalidRootExitsContentFailureStillPrintingTheEnvelope() {
    var result = run("{\"version\":1,\"type\":\"paragraph\"}", "analyze");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(JSON.readTree(result.out()).path("metadata").propertyNames())
        .containsExactlyElementsOf(AdfJson.METADATA_SECTIONS);
    assertThat(result.err()).contains("[ERROR] INVALID_ROOT_NODE:");
  }

  @Test
  void invalidJsonExitsContentFailureInTextToo() {
    var result = run("{ not json", "analyze", "-f", "text", "--select", "outline");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.out()).isEqualTo("outline (0): none\n");
    assertThat(result.err()).contains("[ERROR] INVALID_JSON:");
  }

  @Test
  void blankInputIsInvalidAndSaysSo() {
    var result = run("", "analyze");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.err()).contains("input is blank");
  }

  @Test
  void warningsGoToStderrButKeepTheExitCode() {
    var result = run("{\"version\":999,\"type\":\"doc\",\"content\":[]}", "analyze");
    assertThat(result.exitCode()).isZero();
    assertThat(result.err()).contains("[WARNING] UNSUPPORTED_VERSION:");
    assertThat(result.out()).contains("UNSUPPORTED_VERSION");
  }

  @Test
  void quietSilencesDiagnosticsOnInvalidInput() {
    var result = run("{ not json", "analyze", "-q");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.err()).isEmpty();
  }

  @Test
  void unknownNodesDoNotAbort() {
    assertThat(run(UNKNOWN_NODE_DOC, "analyze").exitCode()).isZero();
  }
}
