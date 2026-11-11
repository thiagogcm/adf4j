package dev.nthings.adf4j.ast;

import java.util.List;

/// An {@link Extension} wrapping a block body: the macro's own `content` blocks, rendered by the
/// engine even when a custom renderer handles the header. Identity and parameters mean what they do
/// on {@link Extension}; an `ExtensionRenderer` here produces only the header line. For several
/// bodies see {@link MultiBodiedExtension}.
public record BodiedExtension(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, AdfExtension, DocContent, LayoutColumnContent {

  public BodiedExtension {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "bodiedExtension";
  }
}
