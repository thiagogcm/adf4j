package dev.nthings.adf4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

class UrlSafetyTests {

  private static final Set<String> POLICY =
      Set.of("http", "https", "mailto", "tel", "ftp", "ftps", "media", "attachment");

  private static final List<Arguments> destinations =
      List.of(
          argumentSet("javascript", "javascript:alert(1)", "javascript%3Aalert(1)"),
          argumentSet("vbscript", "vbscript:msgbox(1)", "vbscript%3Amsgbox(1)"),
          argumentSet("file", "file:///etc/passwd", "file%3A///etc/passwd"),
          argumentSet("data", "data:text/html,<b>x</b>", "data%3Atext/html,<b>x</b>"),
          argumentSet("tab-obfuscated", "java\tscript:alert(1)", "javascript%3Aalert(1)"),
          argumentSet("newline-obfuscated", "java\nscript:alert(1)", "javascript%3Aalert(1)"),
          argumentSet(
              "carriage-return-obfuscated", "java\rscript:alert(1)", "javascript%3Aalert(1)"),
          argumentSet("mixed-case", "JaVaScRiPt:alert(1)", "JaVaScRiPt%3Aalert(1)"),
          argumentSet("leading spaces", "  javascript:alert(1)", "  javascript%3Aalert(1)"),
          argumentSet("leading control", "\u0001javascript:alert(1)", "javascript%3Aalert(1)"),
          argumentSet("already defused", "javascript%3Aalert(1)", "javascript%3Aalert(1)"),
          argumentSet("https", "https://example.com/a?b=c", "https://example.com/a?b=c"),
          argumentSet("uppercase https", "HTTPS://example.com/a", "HTTPS://example.com/a"),
          argumentSet("mailto", "mailto:a@example.com", "mailto:a@example.com"),
          argumentSet("tel", "tel:+123456", "tel:+123456"),
          argumentSet("ftp", "ftp://example.com/file", "ftp://example.com/file"),
          argumentSet("ftps", "ftps://example.com/file", "ftps://example.com/file"),
          argumentSet("media placeholder", "media:collection/id", "media:collection/id"),
          argumentSet("attachment placeholder", "attachment:file-1", "attachment:file-1"),
          argumentSet("root-relative", "/wiki/page", "/wiki/page"),
          argumentSet("dot-relative", "../a/b", "../a/b"),
          argumentSet("fragment", "#section", "#section"),
          argumentSet("query", "?q=1", "?q=1"),
          argumentSet("protocol-relative", "//cdn.example/a", "//cdn.example/a"),
          argumentSet("colon after relative segment", "./a:b", "./a:b"));

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("destinations")
  void allowed_and_relative_destinations_pass_and_other_schemes_are_defused(
      String url, String expected) {
    assertThat(UrlSafety.sanitizeScheme(url, POLICY)).isEqualTo(expected);
  }

  @Test
  void a_policy_selects_its_own_schemes() {
    var webOnly = Set.of("http", "https");

    assertThat(UrlSafety.sanitizeScheme("media:collection/id", webOnly))
        .isEqualTo("media%3Acollection/id");
    assertThat(UrlSafety.sanitizeScheme("https://example.com/a", webOnly))
        .isEqualTo("https://example.com/a");
    assertThat(UrlSafety.sanitizeScheme("/relative", webOnly)).isEqualTo("/relative");
  }
}
