package dev.nthings.adf4j.ast;

import java.util.List;

/// One body region of a {@link MultiBodiedExtension}: a plain container holding that frame's
/// block `content`. It only appears as a direct child of a `multiBodiedExtension`.
public record ExtensionFrame(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock {

  public ExtensionFrame {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "extensionFrame";
  }
}
