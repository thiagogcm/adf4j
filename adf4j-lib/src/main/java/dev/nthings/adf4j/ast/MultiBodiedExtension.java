package dev.nthings.adf4j.ast;

import java.util.List;

/// An extension whose body is split across several {@link ExtensionFrame}s, one per body region
/// (e.g. tabs). Identity and parameters are as on {@link Extension}.
public record MultiBodiedExtension(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, AdfExtension {

  public MultiBodiedExtension {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "multiBodiedExtension";
  }
}
