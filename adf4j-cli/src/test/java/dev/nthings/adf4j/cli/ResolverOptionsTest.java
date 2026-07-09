package dev.nthings.adf4j.cli;

import static dev.nthings.adf4j.cli.CliTestSupport.MEDIA_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.SIMPLE_DOC;
import static dev.nthings.adf4j.cli.CliTestSupport.convert;
import static dev.nthings.adf4j.cli.CliTestSupport.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResolverOptionsTest {

  @Test
  void mediaUrlTemplateResolvesFileMedia() {
    var result = convert(MEDIA_DOC, "--media-url", "https://cdn.example/{collection}/{id}");
    assertThat(result.out()).contains("https://cdn.example/col/FILE_ID");
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
    assertThat(result.out()).contains("https://map.example/win");
    assertThat(result.out()).doesNotContain("cdn.example");
  }

  @Test
  void unknownPlaceholderForFlagIsAUsageError() {
    var result =
        convert(MEDIA_DOC, "--media-url", "https://cdn/{pageId}"); // pageId invalid for media
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("unknown placeholder '{pageId}'");
  }

  @Test
  void templateValueIsPercentEncodedToDefuseTraversal() {
    var doc = MEDIA_DOC.replace("\"FILE_ID\"", "\"../escape\"");
    var result = convert(doc, "--media-url", "https://cdn/{collection}/{id}");
    assertThat(result.out()).contains("col/..%2Fescape"); // the '/' in the id is encoded
    assertThat(result.out()).doesNotContain("col/../escape"); // no live traversal segment
  }

  @Test
  void templateValueEncodesSupplementaryCharactersAsUtf8() {
    var doc = MEDIA_DOC.replace("\"FILE_ID\"", "\"\\uD83D\\uDE00\""); // 😀 (U+1F600)
    var result = convert(doc, "--media-url", "https://cdn/{collection}/{id}");
    assertThat(result.out()).contains("col/%F0%9F%98%80"); // the code point's UTF-8 bytes
    assertThat(result.out()).doesNotContain("%3F"); // not two replacement '?' bytes
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
    var result = convert(SIMPLE_DOC, "--attachments-map", map.toString());
    assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(result.err()).contains("unknown key 'bogusKey'");
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
