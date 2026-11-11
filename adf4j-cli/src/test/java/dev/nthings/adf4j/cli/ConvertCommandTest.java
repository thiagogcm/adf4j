package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.LOSSY_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.UNKNOWN_NODE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.convert;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ConvertCommandTest {

  @Test
  void convertsStdinToBodyOnStdout() {
    var result = convert(SIMPLE_DOC);
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).isEqualTo("# Hello\n\nWorld");
  }

  @Test
  void readsFromFilePositional(@TempDir Path dir) {
    var input = CliTestSupport.write(dir, "doc.json", SIMPLE_DOC);
    var result = convert("", input.toString());
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).isEqualTo("# Hello\n\nWorld");
  }

  @Test
  void titleAndRenderingFlagsApply() {
    var result = convert(SIMPLE_DOC, "-t", "My Page");
    assertThat(result.out())
        .isEqualTo(
            """
            # My Page

            # Hello

            World""");
  }

  @Test
  void jsonFormatEmitsFullResultPrettyByDefault() {
    var result = convert(SIMPLE_DOC, "-f", "json");
    assertThat(result.exitCode()).isZero();
    assertThat(result.out())
        .isEqualTo(
            """
            {
              "ok" : true,
              "body" : "# Hello\\n\\nWorld",
              "hasWarnings" : false,
              "diagnostics" : [ ],
              "metadata" : {
                "pageIds" : [ ],
                "externalUrls" : [ ],
                "mentionRefs" : [ ],
                "attachmentRefs" : [ ],
                "referencedFileIds" : [ ],
                "pageTreeRefs" : [ ],
                "excerptRefs" : [ ],
                "excerpts" : [ ],
                "outline" : [ {
                  "level" : 1,
                  "text" : "Hello",
                  "anchor" : "hello"
                } ]
              },
              "effects" : [ ],
              "unresolved" : [ ]
            }
            """);
  }

  @Test
  void jsonMetadataDescribesExcerptsWithoutRenderingThemAgain() {
    var excerpt =
        """
        {"type":"doc","version":1,"content":[{"type":"bodiedExtension","attrs":{
          "extensionType":"com.atlassian.confluence.macro.core","extensionKey":"excerpt"},
          "content":[{"type":"paragraph","content":[{"type":"text","text":"Shared"}]}]}]}
        """;
    var result = convert(excerpt, "-f", "json", "-t", "Page");
    assertThat(result.exitCode()).isZero();
    assertThat(result.out())
        .isEqualTo(
            """
            {
              "ok" : true,
              "body" : "# Page\\n\\nShared",
              "hasWarnings" : false,
              "diagnostics" : [ ],
              "metadata" : {
                "pageIds" : [ ],
                "externalUrls" : [ ],
                "mentionRefs" : [ ],
                "attachmentRefs" : [ ],
                "referencedFileIds" : [ ],
                "pageTreeRefs" : [ ],
                "excerptRefs" : [ ],
                "excerpts" : [ {
                  "name" : null
                } ],
                "outline" : [ ]
              },
              "effects" : [ ],
              "unresolved" : [ ]
            }
            """);
  }

  @Test
  void jsonNamesFollowTheApi(@TempDir Path dir) {
    var inventory =
        CliTestSupport.write(
            dir, "inventory.json", "[{\"fileId\":\"FILE_ID\",\"downloadUrl\":\"https://x/f\"}]");
    var result =
        convert(CliTestSupport.MEDIA_DOC, "-f", "json", "--attachments", inventory.toString());
    assertThat(result.out())
        .isEqualTo(
            """
            {
              "ok" : true,
              "body" : "[f](https://x/f)",
              "hasWarnings" : false,
              "diagnostics" : [ ],
              "metadata" : {
                "pageIds" : [ ],
                "externalUrls" : [ ],
                "mentionRefs" : [ ],
                "attachmentRefs" : [ {
                  "fileId" : "FILE_ID",
                  "title" : null,
                  "mediaType" : null,
                  "downloadUrl" : "https://x/f"
                } ],
                "referencedFileIds" : [ "FILE_ID" ],
                "pageTreeRefs" : [ ],
                "excerptRefs" : [ ],
                "excerpts" : [ ],
                "outline" : [ ]
              },
              "effects" : [ ],
              "unresolved" : [ ]
            }
            """);
  }

  @Test
  void unresolvedPageTreeIdentifiesTheSourceAndFallback() {
    var pageTree =
        """
        {"type":"doc","version":1,"content":[{"type":"extension","attrs":{
          "extensionType":"com.atlassian.confluence.macro.core","extensionKey":"children",
          "parameters":{"macroParams":{"depth":{"value":"2"},"all":{"value":"true"}}}}}]}
        """;
    var result = convert(pageTree, "-f", "json", "--compact");
    var output = JsonMapper.builder().build().readTree(result.out());
    assertThat(output.get("unresolved").get(0).get("path").asString()).isEqualTo("/content/0");
    assertThat(output.get("unresolved").get(0).get("fallback").asString())
        .isEqualTo("{{children}}");
    assertThat(output.get("metadata").get("pageTreeRefs").get(0).get("depth").asInt()).isEqualTo(2);
  }

  @Test
  void compactFlagProducesSingleLineJson() {
    var result = convert(SIMPLE_DOC, "-f", "json", "--compact");
    assertThat(result.out().strip().lines().count()).isEqualTo(1);
    assertThat(result.out())
        .isEqualTo(
            """
            {"ok":true,"body":"# Hello\\n\\nWorld","hasWarnings":false,"diagnostics":[],"metadata":{"pageIds":[],"externalUrls":[],"mentionRefs":[],"attachmentRefs":[],"referencedFileIds":[],"pageTreeRefs":[],"excerptRefs":[],"excerpts":[],"outline":[{"level":1,"text":"Hello","anchor":"hello"}]},"effects":[],"unresolved":[]}
            """);
  }

  @Test
  void writesToOutputFileLeavingStdoutEmpty(@TempDir Path dir) throws Exception {
    var out = dir.resolve("out.md");
    var result = convert(SIMPLE_DOC, "-o", out.toString());
    assertThat(result.exitCode()).isZero();
    assertThat(result.out()).isEmpty();
    assertThat(Files.readString(out)).isEqualTo("# Hello\n\nWorld");
  }

  @Test
  void outputFileGetsTheUmaskDefaultMode(@TempDir Path dir) throws Exception {
    assumePosix(dir);
    // A file made without an explicit mode shows the umask default.
    var expected = Files.getPosixFilePermissions(Files.createFile(dir.resolve("reference")));
    var out = dir.resolve("out.md");
    var result = convert(SIMPLE_DOC, "-o", out.toString());
    assertThat(result.exitCode()).isZero();
    assertThat(Files.getPosixFilePermissions(out)).isEqualTo(expected);
    assertThat(leftovers(dir)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"rw-r-----", "r--r--r--"})
  void outputFileKeepsAnExistingFilesMode(String mode, @TempDir Path dir) throws Exception {
    assumePosix(dir);
    var out = CliTestSupport.write(dir, "out.md", "old");
    var permissions = PosixFilePermissions.fromString(mode);
    Files.setPosixFilePermissions(out, permissions);
    var result = convert(SIMPLE_DOC, "-o", out.toString());
    assertThat(result.exitCode()).isZero();
    assertThat(Files.readString(out)).isEqualTo("# Hello\n\nWorld");
    assertThat(Files.getPosixFilePermissions(out)).isEqualTo(permissions);
    assertThat(leftovers(dir)).isEmpty();
  }

  @Test
  void outputThroughASymlinkKeepsTheLink(@TempDir Path dir) throws Exception {
    assumePosix(dir); // symlinks need a privilege on Windows
    var elsewhere = Files.createDirectory(dir.resolve("elsewhere"));
    var real = CliTestSupport.write(elsewhere, "real.md", "old");
    var link = Files.createSymbolicLink(dir.resolve("link.md"), real);
    var result = convert(SIMPLE_DOC, "-o", link.toString());
    assertThat(result.exitCode()).isZero();
    assertThat(Files.isSymbolicLink(link)).isTrue();
    assertThat(Files.readString(real)).isEqualTo("# Hello\n\nWorld");
    assertThat(leftovers(elsewhere)).isEmpty();
  }

  @Test
  void diagnosticsSummaryGoesToStderrNotStdout() {
    var result = convert(LOSSY_DOC, "-f", "json");
    assertThat(result.out()).doesNotContain("warning(s)");
    assertThat(result.err()).contains("warning(s)");
  }

  @Test
  void quietSuppressesTheSummary() {
    var result = convert(LOSSY_DOC, "-q");
    assertThat(result.err()).isEmpty();
  }

  @Test
  void malformedAdfExitsContentFailureStillWritingStdout() {
    var result = convert("{ not json", "-t", "Page");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.out()).isEmpty();
    assertThat(result.err()).contains("1 error(s)").contains("[ERROR] INVALID_JSON:");
  }

  @Test
  void invalidRootExitsContentFailureWithAJsonResult() {
    var result = convert("{\"version\":1,\"type\":\"paragraph\"}", "-f", "json");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.out())
        .isEqualTo(
            """
            {
              "ok" : false,
              "body" : "",
              "hasWarnings" : false,
              "diagnostics" : [ {
                "code" : "INVALID_ROOT_NODE",
                "severity" : "ERROR",
                "message" : "ADF root node must have type 'doc'.",
                "paths" : [ "/type" ],
                "details" : { }
              }, {
                "code" : "INVALID_CONTENT",
                "severity" : "ERROR",
                "message" : "ADF root must contain array field 'content'.",
                "paths" : [ "/content" ],
                "details" : { }
              } ],
              "metadata" : {
                "pageIds" : [ ],
                "externalUrls" : [ ],
                "mentionRefs" : [ ],
                "attachmentRefs" : [ ],
                "referencedFileIds" : [ ],
                "pageTreeRefs" : [ ],
                "excerptRefs" : [ ],
                "excerpts" : [ ],
                "outline" : [ ]
              },
              "effects" : [ ],
              "unresolved" : [ ],
              "error" : "ADF root node must have type 'doc'."
            }
            """);
  }

  @Test
  void blankInputIsInvalidAndSaysSo() {
    var result = convert("  ");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.out()).isEmpty();
    assertThat(result.err()).contains("input is blank");
  }

  @Test
  void invalidInputOutranksTheWarningGate() {
    var result = convert("{ not json", "--fail-on-warning");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
  }

  @Test
  void failOnWarningExitsFourWhenWarningPresent() {
    var result = convert(LOSSY_DOC, "--fail-on-warning");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.QUALITY_GATE);
  }

  @Test
  void failOnWarningExitsZeroWhenClean() {
    var result = convert(SIMPLE_DOC, "--fail-on-warning");
    assertThat(result.exitCode()).isZero();
  }

  @Test
  void unknownNodesFailAbortsWithContentFailure() {
    var result = convert(UNKNOWN_NODE_DOC, "--unknown-nodes", "fail");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.CONTENT_FAILURE);
    assertThat(result.err()).contains("UNKNOWN_NODE_REJECTED").contains("bogusBlock");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void invalidEnumValueIsAUsageError() {
    var result = convert(SIMPLE_DOC, "--unknown-nodes", "bogus");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("--unknown-nodes").contains("Allowed values");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void jsonDiagnosticsNeverLeakStackFramesOrCause() {
    var result = convert("{ definitely not valid json", "-f", "json");
    assertThat(result.out()).contains("INVALID_JSON");
    assertThat(result.out()).doesNotContain("\tat ");
    assertThat(result.out()).doesNotContain("Exception");
  }

  @Test
  void configInventoryResolvesFilesAndExplicitTitleOverridesTheConfig(@TempDir Path dir) {
    var config =
        CliTestSupport.write(
            dir,
            "config.json",
            """
        {"context":{"title":"Original","attachments":[
          {"fileId":"FILE_ID","downloadUrl":"downloads/file"}]}}
        """);
    var missing = convert(CliTestSupport.MEDIA_DOC, "--fail-on-unresolved");
    assertThat(missing.exitCode()).isEqualTo(ExitCodes.QUALITY_GATE);
    assertThat(missing.out()).isEqualTo("[file](media:col/FILE_ID)");

    var resolved =
        convert(
            CliTestSupport.MEDIA_DOC,
            "--config",
            config.toString(),
            "--title",
            "Published",
            "--fail-on-unresolved");
    assertThat(resolved.exitCode()).isZero();
    assertThat(resolved.out())
        .isEqualTo(
            """
            # Published

            [file](downloads/file)""");
  }

  @Test
  void presentationChangeGateWorksWithoutWarnings() {
    var styled =
        """
        {"type":"doc","version":1,"content":[{"type":"paragraph","content":[
          {"type":"text","text":"Important","marks":[{"type":"textColor","attrs":{"color":"#ff0000"}}]}]}]}
        """;
    assertThat(convert(styled, "--fail-on-warning").exitCode()).isZero();
    var gated = convert(styled, "--fail-on-changes");
    assertThat(gated.exitCode()).isEqualTo(ExitCodes.QUALITY_GATE);
    assertThat(gated.out()).isEqualTo("Important");
  }

  private static void assumePosix(Path dir) {
    Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"));
  }

  private static List<Path> leftovers(Path dir) throws IOException {
    try (var files = Files.list(dir)) {
      return files.filter(file -> file.getFileName().toString().startsWith(".adf4j-")).toList();
    }
  }
}
