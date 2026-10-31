package dev.nthings.adf4j.ast;

import java.util.List;

/// An extension whose body is split across several frames: its `content` is a list of
/// {@link ExtensionFrame} blocks, one per body region (e.g. a tabbed/multi-pane macro). The
/// identity and parameter components carry the same meaning as on
/// {@link Extension}/{@link BodiedExtension}; each frame's own blocks live inside its
/// {@link ExtensionFrame}.
public record MultiBodiedExtension(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, AdfExtension {

  public MultiBodiedExtension {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "multiBodiedExtension";
  }
}
