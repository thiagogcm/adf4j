package dev.nthings.adf4j.internal.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MacroDiagnosticsTests {

  @Test
  void recording_reports_whether_this_render_had_seen_the_label() {
    var diagnostics = new MacroDiagnostics();

    assertThat(diagnostics.recordUnsupported("type", "key")).isTrue();
    assertThat(diagnostics.recordUnsupported("type", "key")).isFalse();
    assertThat(diagnostics.recordUnsupported("type", "other")).isTrue();
  }

  @Test
  void each_render_starts_fresh() {
    new MacroDiagnostics().recordUnsupported("type", "key");

    assertThat(new MacroDiagnostics().recordUnsupported("type", "key")).isTrue();
  }
}
