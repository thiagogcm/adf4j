package dev.nthings.adf4j.internal.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Attachments;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Chart;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Excerpt;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.ExcerptInclude;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Iframe;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.InlineMediaImage;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.LegacyChart;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Other;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.PageTree;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Toc;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.ViewPdf;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.PageTreeMacro;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfluenceMacroTests {

  private static final String MACRO = "com.atlassian.confluence.macro.core";
  private static final String MIGRATION = "com.atlassian.confluence.migration";

  @Test
  void a_macro_is_classified_by_its_key() {
    assertThat(classify(MACRO, "toc", Map.of("minLevel", "2", "maxLevel", "3")))
        .isEqualTo(new Toc(new TocLevelRange(2, 3)));
    assertThat(classify(MACRO, "iframe", Map.of("src", "https://x")))
        .isEqualTo(new Iframe("https://x"));
    assertThat(classify(MACRO, "viewpdf", Map.of())).isEqualTo(new ViewPdf());
    assertThat(classify(MACRO, "attachments", Map.of())).isEqualTo(new Attachments());
    assertThat(classify(MACRO, "excerpt", Map.of("name", " intro ")))
        .isEqualTo(new Excerpt("intro"));
    assertThat(classify(MACRO, "excerpt", Map.of())).isEqualTo(new Excerpt(null));
  }

  @Test
  void a_page_tree_root_ignores_blanks_and_keywords() {
    assertThat(classify(MACRO, "pagetree", Map.of("root", " Home ")))
        .isInstanceOfSatisfying(
            PageTree.class,
            tree -> {
              assertThat(tree.reference().macro()).isEqualTo(PageTreeMacro.PAGETREE);
              assertThat(tree.reference().root()).isEqualTo("Home");
            });
    assertThat(classify(MACRO, "children", Map.of("page", "@self")))
        .isInstanceOfSatisfying(
            PageTree.class,
            tree -> {
              assertThat(tree.reference().macro()).isEqualTo(PageTreeMacro.CHILDREN);
              assertThat(tree.reference().root()).isNull();
            });
  }

  @Test
  void an_excerpt_include_needs_a_source_page() {
    assertThat(classify(MACRO, "excerpt-include", Map.of("", " Page ", "name", " part ")))
        .isEqualTo(
            new ExcerptInclude(
                new ExcerptIncludeReference(
                    "Page", "part", Map.of("", " Page ", "name", " part "))));
    assertThat(classify(MACRO, "excerpt-include", Map.of("name", "part"))).isEqualTo(new Other());
    assertThat(classify(MACRO, "excerpt-include", Map.of("", "  "))).isEqualTo(new Other());
  }

  @Test
  void only_chart_default_is_a_labelled_legacy_chart() {
    assertThat(classify(MACRO, "chart:default", Map.of())).isEqualTo(new LegacyChart(true));
    assertThat(classify(MACRO, "chart", Map.of())).isEqualTo(new LegacyChart(false));
    assertThat(classify(MACRO, "chart:bar", Map.of())).isEqualTo(new LegacyChart(false));
    assertThat(classify(MACRO, "chartish", Map.of())).isEqualTo(new Other());
  }

  @Test
  void the_chart_app_is_a_chart_whatever_its_key() {
    assertThat(classify("com.atlassian.chart", "anything", Map.of())).isEqualTo(new Chart());
    assertThat(classify("com.atlassian.chart", null, Map.of())).isEqualTo(new Chart());
  }

  @Test
  void an_inline_media_image_needs_a_media_id() {
    var withId =
        new InlineExtension(
            attributes(
                MIGRATION,
                "inline-media-image",
                Map.of("id", 12, "width", 80, "collection", " c ")),
            List.of());
    assertThat(ConfluenceMacro.of(withId))
        .isInstanceOfSatisfying(
            InlineMediaImage.class,
            image -> {
              assertThat(image.media().id()).isEqualTo("12");
              assertThat(image.media().width()).isEqualTo("80");
              assertThat(image.media().collection()).isEqualTo("c");
              assertThat(image.media().mediaType()).isEqualTo("image");
              assertThat(image.media().marks()).isEmpty();
            });

    var withoutId =
        new Extension(attributes(MIGRATION, "inline-media-image", Map.of("id", " ")), List.of());
    assertThat(ConfluenceMacro.of(withoutId)).isEqualTo(new Other());
  }

  @Test
  void anything_else_is_other() {
    assertThat(classify("x", "toc", Map.of())).isEqualTo(new Other());
    assertThat(classify(null, "toc", Map.of())).isEqualTo(new Other());
    assertThat(classify(MACRO, null, Map.of())).isEqualTo(new Other());
    assertThat(classify(MACRO, "detailssummary", Map.of())).isEqualTo(new Other());
    assertThat(classify(MIGRATION, "other", Map.of())).isEqualTo(new Other());
  }

  @Test
  void every_extension_shape_classifies_the_same() {
    var attrs = attributes(MACRO, "toc", Map.of());
    assertThat(ConfluenceMacro.of(new BodiedExtension(attrs, List.of(), List.of())))
        .isEqualTo(ConfluenceMacro.of(new InlineExtension(attrs, List.of())));
  }

  private static ConfluenceMacro classify(
      String type, String key, Map<String, String> macroParams) {
    var parameters = Map.<String, Object>of("macroParams", macroParams);
    return ConfluenceMacro.of(new Extension(attributes(type, key, parameters), List.of()));
  }

  private static Attributes attributes(String type, String key, Map<String, ?> parameters) {
    var attrs = new java.util.HashMap<String, Object>();
    if (type != null) {
      attrs.put("extensionType", type);
    }
    if (key != null) {
      attrs.put("extensionKey", key);
    }
    attrs.put("parameters", parameters);
    return new Attributes(attrs);
  }
}
