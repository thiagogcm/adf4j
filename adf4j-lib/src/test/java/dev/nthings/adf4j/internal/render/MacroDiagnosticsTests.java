package dev.nthings.adf4j.internal.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MacroDiagnosticsTests {

  @Test
  void recording_reports_whether_this_render_had_seen_the_label() {
    var diagnostics = new MacroDiagnostics();

    assertThat(diagnostics.recordUnsupported("type", "key", "/content/0")).isTrue();
    assertThat(diagnostics.recordUnsupported("type", "key", "/content/0")).isFalse();
    assertThat(diagnostics.recordUnsupported("type", "other", "/content/0")).isTrue();
  }
}
