package dev.nthings.adf4j.renderer;

import dev.nthings.adf4j.model.BlockStyles;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderingStrategiesTests {

  @Test
  void storage_strategy_keeps_markdown_friendly_output_rules() {
    var strategy = RenderingStrategies.storage();

    assertThat(strategy.formatParagraph("Body", new BlockStyles("center", 1, "14px")))
        .isEqualTo("Body");
    assertThat(strategy.formatHeading(2, "Section", "custom-section", BlockStyles.none()))
        .isEqualTo("<a id=\"custom-section\"></a>\n## Section");
    assertThat(strategy.isStorage()).isTrue();
    assertThat(strategy.omitsVisualOnlyMarks()).isTrue();
    assertThat(strategy.usesStyledInlineMedia()).isFalse();
  }

  @Test
  void presentation_strategy_keeps_html_specific_formatting_rules() {
    var strategy = RenderingStrategies.presentation();
    var blockStyles = new BlockStyles("center", 1, "14px");

    assertThat(strategy.formatParagraph("Body", blockStyles))
        .isEqualTo("<p style=\"text-align:center; margin-left:2em; font-size:14px\">Body</p>");
    assertThat(strategy.formatHeading(2, "Section", "custom-section", blockStyles))
        .isEqualTo(
            "<h2 id=\"custom-section\" style=\"text-align:center; margin-left:2em; font-size:14px\">Section</h2>");
    assertThat(strategy.isStorage()).isFalse();
    assertThat(strategy.omitsVisualOnlyMarks()).isFalse();
    assertThat(strategy.usesStyledInlineMedia()).isTrue();
  }
}
