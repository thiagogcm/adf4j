package dev.nthings.adf4j.ast;

import java.util.List;

/// One body region of a {@link MultiBodiedExtension}: a plain container holding that frame's
/// block `content`. It only appears as a direct child of a `multiBodiedExtension`.
public record ExtensionFrame(List<AdfBlock> content) implements AdfBlock {

  public ExtensionFrame {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
