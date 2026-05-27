package dev.nthings.adf4j;

import dev.nthings.adf4j.model.UnknownNodePolicy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderOptionsTests {

  @Test
  void defaults_use_placeholder_policy_and_no_render_context() {
    var options = RenderOptions.defaults();

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.PLACEHOLDER);
    assertThat(options.context()).isSameAs(AdfRenderContext.none());
  }

  @Test
  void constructor_and_copy_methods_normalize_null_policy_and_context() {
    var options = new RenderOptions(null, null);

    assertThat(options.unknownNodePolicy()).isEqualTo(UnknownNodePolicy.PLACEHOLDER);
    assertThat(options.context()).isSameAs(AdfRenderContext.none());
    assertThat(options.withUnknownNodePolicy(UnknownNodePolicy.SKIP).unknownNodePolicy())
        .isEqualTo(UnknownNodePolicy.SKIP);
    assertThat(options.withContext(null).context()).isSameAs(AdfRenderContext.none());
  }
}
