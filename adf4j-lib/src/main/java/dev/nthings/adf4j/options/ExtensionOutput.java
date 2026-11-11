package dev.nthings.adf4j.options;

import java.util.Objects;

/// Custom extension output and its ownership `mode`. Markdown is inserted verbatim.
public record ExtensionOutput(Mode mode, String markdown) {
  public enum Mode {
    DEFER,
    REPLACE,
    HEADER
  }

  public ExtensionOutput {
    Objects.requireNonNull(mode);
    Objects.requireNonNull(markdown);
  }

  public static ExtensionOutput defer() {
    return new ExtensionOutput(Mode.DEFER, "");
  }

  public static ExtensionOutput replace(String markdown) {
    return new ExtensionOutput(Mode.REPLACE, markdown);
  }

  public static ExtensionOutput header(String markdown) {
    return new ExtensionOutput(Mode.HEADER, markdown);
  }
}
