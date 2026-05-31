package dev.nthings.adf4j;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.UnknownNodePolicy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderOptionsTests {

  @Test
  void defaults_use_placeholder_policy_and_empty_render_context() {
    var options = RenderOptions.defaults();

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.PLACEHOLDER);
    assertThat(options.context()).isEqualTo(ConfluenceRenderContext.empty());
  }

  @Test
  void constructor_and_copy_methods_normalize_null_policy_and_context() {
    var options = new RenderOptions(null, null);

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.PLACEHOLDER);
    assertThat(options.context()).isEqualTo(ConfluenceRenderContext.empty());
    assertThat(options.withUnknownNodePolicy(UnknownNodePolicy.SKIP).unknownNodePolicy())
        .isEqualTo(UnknownNodePolicy.SKIP);
    assertThat(options.withContext(null).context()).isEqualTo(ConfluenceRenderContext.empty());
  }
}
