package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.MEDIA_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.convert;
import static dev.nthings.adf4j.cli.CliTestSupport.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResolverOptionsTest {

  @Test
  void mediaUrlTemplateResolvesFileMedia() {
    var result = convert(MEDIA_DOC, "--media-url", "https://cdn.example/{collection}/{id}");
    assertThat(result.out()).isEqualTo("[FILE_ID](https://cdn.example/col/FILE_ID)");
  }

  @Test
  void mediaWithoutAnIdKeepsThePlaceholderInsteadOfAnEmptySegment() {
    var doc =
        "{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"mediaSingle\",\"content\":["
            + "{\"type\":\"media\",\"attrs\":{\"type\":\"file\",\"collection\":\"col\"}}]}]}";

    var result = convert(doc, "--media-url", "https://cdn.example/{collection}/{id}");

    assertThat(result.out()).isEqualTo("[file](media)");
  }

  @Test
  void mediaMapWinsOverTemplate(@TempDir Path dir) {
    var map = write(dir, "media.json", "{\"FILE_ID\":\"https://map.example/win\"}");
    var result =
        convert(
            MEDIA_DOC,
            "--media-map",
            map.toString(),
            "--media-url",
            "https://cdn.example/{collection}/{id}");
    assertThat(result.out()).isEqualTo("[win](https://map.example/win)");
  }

  @Test
  void unknownPlaceholderForFlagIsAUsageError() {
    var result = convert(MEDIA_DOC, "--media-url", "https://cdn/{pageId}");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("unknown placeholder '{pageId}'");
  }

  @Test
  void templateValueIsPercentEncodedToDefuseTraversal() {
    var doc = MEDIA_DOC.replace("\"FILE_ID\"", "\"../escape\"");
    var result = convert(doc, "--media-url", "https://cdn/{collection}/{id}");
    assertThat(result.out())
        .isEqualTo("[..%2Fescape](https://cdn/col/..%2Fescape)"); // '/' is encoded
    assertThat(result.out()).isEqualTo("[..%2Fescape](https://cdn/col/..%2Fescape)");
  }

  @Test
  void templateValueEncodesSupplementaryCharactersAsUtf8() {
    var doc = MEDIA_DOC.replace("\"FILE_ID\"", "\"\\uD83D\\uDE00\""); // 😀 (U+1F600)
    var result = convert(doc, "--media-url", "https://cdn/{collection}/{id}");
    assertThat(result.out())
        .isEqualTo("[%F0%9F%98%80](https://cdn/col/%F0%9F%98%80)"); // the code point's UTF-8 bytes
    assertThat(result.out())
        .isEqualTo("[%F0%9F%98%80](https://cdn/col/%F0%9F%98%80)"); // not two replacement '?' bytes
  }

  @Test
  void missingMapFileIsAnIoError() {
    var result = convert(SIMPLE_DOC, "--media-map", "/no/such/file.json");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.IO);
    assertThat(result.err()).contains("not found");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void malformedMapFileIsAUsageErrorWithNoStdout(@TempDir Path dir) {
    var map = write(dir, "bad.json", "{ this is not json");
    var result = convert(SIMPLE_DOC, "--media-map", map.toString());
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("invalid JSON in --media-map");
    assertThat(result.out()).isEmpty();
    assertThat(result.err()).doesNotContain("\tat "); // no stack trace without -v
  }

  @Test
  void unknownKeyInMapSchemaIsRejected(@TempDir Path dir) {
    var map = write(dir, "att.json", "[{\"fileId\":\"x\",\"bogusKey\":\"y\"}]");
    var result = convert(SIMPLE_DOC, "--attachments", map.toString());
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("unknown key 'bogusKey'");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '`',
      value = {
        "--media-map      | {\"f1\":{\"a\":1}}                                  | 'f1'",
        "--media-map      | {\"f1\":[1]}                                        | 'f1'",
        "--media-map      | [1]                                                 | JSON object",
        "--attachment-map | {\"f1\":{\"a\":1}}                                  | 'f1'",
        "--page-map       | {\"p1\":{\"a\":1}}                                  | 'p1'",
        "--excerpt-map    | [{\"page\":{\"a\":1}}]                                | 'page'",
        "--excerpt-map    | [{\"page\":\"p\",\"name\":[]}]                        | 'name'",
        "--excerpt-map    | [1]                                                 | JSON object",
        "--excerpt-map    | {}                                                  | JSON array",
        "--extension-map  | [{\"key\":\"k\",\"template\":{}}]                       | 'template'",
        "--extension-map  | [{\"key\":\"k\",\"bogus\":1}]                           | 'bogus'",
        "--attachments    | {}                                                  | JSON array",
        "--attachments    | [\"x\"]                                               | JSON object",
        "--attachments    | [{\"title\":\"no file id\"}]                            | 'fileId'",
        "--attachments    | [{\"fileId\":\"f\",\"title\":[]}]                       | 'title'",
        "--page-tree-map  | []                                                  | JSON object",
        "--page-tree-map  | {\"bogus\":{}}                                        | 'bogus'",
        "--page-tree-map  | {\"pagetree\":[]}                                     | JSON object",
        "--page-tree-map  | {\"pagetree\":{\"r\":{}}}                               | JSON array",
        "--page-tree-map  | {\"pagetree\":{\"r\":[1]}}                              | JSON object",
        "--page-tree-map  | {\"pagetree\":{\"r\":[{\"bogus\":1}]}}                    | 'bogus'",
        "--page-tree-map  | {\"pagetree\":{\"r\":[{\"depth\":\"x\"}]}}                  | 'depth'",
        "--page-tree-map  | {\"children\":{\"r\":[{\"depth\":1.5}]}}                  | 'depth'",
        "--page-tree-map  | {\"children\":{\"r\":[{\"depth\":{}}]}}                   | 'depth'",
        "--page-tree-map  | {\"children\":{\"r\":[{\"title\":{\"a\":1}}]}}             | 'title'",
      })
  void badMapFilesAreUsageErrors(String flag, String content, String named, @TempDir Path dir) {
    var map = write(dir, "map.json", content);
    var result = convert(SIMPLE_DOC, flag, map.toString());
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains(flag).contains(named).doesNotContain("internal error");
    assertThat(result.out()).isEmpty();
  }

  @Test
  void numericScalarsStayAllowed(@TempDir Path dir) {
    var media = write(dir, "media.json", "{\"FILE_ID\":12345}");
    assertThat(convert(MEDIA_DOC, "--media-map", media.toString()).out())
        .isEqualTo("[12345](12345)");

    var tree =
        write(
            dir,
            "tree.json",
            "{\"pagetree\":{\"\":[{\"depth\":\"1\",\"title\":7},{\"depth\":2,\"title\":8}]}}");
    var doc =
        """
        {"type":"doc","version":1,"content":[{"type":"extension","attrs":{
          "extensionType":"com.atlassian.confluence.macro.core","extensionKey":"pagetree"}}]}
        """;
    assertThat(convert(doc, "--page-tree-map", tree.toString()).out())
        .isEqualTo(
            """
            - 7
              - 8""");
  }

  @Test
  void excerptMapEmitsVerbatimWarning(@TempDir Path dir) {
    var map = write(dir, "ex.json", "[]");
    var result = convert(SIMPLE_DOC, "--excerpt-map", map.toString());
    assertThat(result.err()).contains("--excerpt-map content is emitted verbatim");
  }

  @Test
  void quietSuppressesVerbatimWarning(@TempDir Path dir) {
    var map = write(dir, "ex.json", "[]");
    var result = convert(SIMPLE_DOC, "-q", "--excerpt-map", map.toString());
    assertThat(result.err()).doesNotContain("verbatim");
  }
}
