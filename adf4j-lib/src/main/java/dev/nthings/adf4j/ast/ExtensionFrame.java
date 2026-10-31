package dev.nthings.adf4j.ast;

import java.util.List;

/// One body region of a {@link MultiBodiedExtension}: a plain container holding that frame's
/// block `content`. It only appears as a direct child of a `multiBodiedExtension`.
public record ExtensionFrame(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock {

  public ExtensionFrame {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "extensionFrame";
  }
}
