package dev.nthings.adf4j.rendering;

import static dev.nthings.adf4j.ast.Adf.doc;
import static dev.nthings.adf4j.ast.Adf.extension;
import static dev.nthings.adf4j.ast.Adf.inlineCard;
import static dev.nthings.adf4j.ast.Adf.link;
import static dev.nthings.adf4j.ast.Adf.macroParameters;
import static dev.nthings.adf4j.ast.Adf.paragraph;
import static dev.nthings.adf4j.ast.Adf.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import dev.nthings.adf4j.AdfToMarkdown;
import dev.nthings.adf4j.ast.AdfDocument;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

/// Destinations from untrusted ADF: link marks, cards and iframe macros defuse script-capable
/// schemes, keep safe ones untouched, and escape what lands in link text.
class LinkSafetyTests {

  private static final List<Arguments> destinations =
      List.of(
          argumentSet(
              "javascript link",
              linked("javascript:alert(document.cookie)"),
              "[x](javascript%3Aalert(document.cookie))"),
          argumentSet(
              "data URL link",
              linked("data:text/html,<script>alert(1)</script>"),
              "[x](data%3Atext/html,%3Cscript%3Ealert%281%29%3C/script%3E)"),
          argumentSet(
              "tab-obfuscated javascript link",
              linked("java\tscript:alert(1)"),
              "[x](javascript%3Aalert(1))"),
          argumentSet("https link", linked("https://example.com/a"), "[x](https://example.com/a)"),
          argumentSet("mailto link", linked("mailto:a@example.com"), "[x](mailto:a@example.com)"),
          argumentSet("relative link", linked("/wiki/page"), "[x](/wiki/page)"),
          argumentSet(
              "javascript card",
              doc(paragraph(inlineCard("javascript:alert(1)"))),
              "[javascript:alert(1)](javascript%3Aalert(1))"),
          argumentSet(
              "card label escaped, destination kept",
              doc(paragraph(inlineCard("/wiki/a*b*c"))),
              "[/wiki/a\\*b\\*c](/wiki/a*b*c)"),
          argumentSet(
              "javascript iframe",
              iframe("javascript:alert(1)"),
              "[Embedded content](javascript%3Aalert(1))"),
          argumentSet(
              "iframe source with spaces",
              iframe("https://e.com/a (b)"),
              "[Embedded content](<https://e.com/a (b)>)"));

  @ParameterizedTest(name = "{argumentSetName}")
  @FieldSource("destinations")
  void destinations_are_defused_or_kept(AdfDocument document, String markdown) {
    assertThat(AdfToMarkdown.create().convert(document).body()).isEqualTo(markdown);
  }

  private static AdfDocument linked(String href) {
    return doc(paragraph(text("x", link(href))));
  }

  private static AdfDocument iframe(String src) {
    return doc(
        extension(
            "com.atlassian.confluence.macro.core", "iframe", macroParameters(Map.of("src", src))));
  }
}
