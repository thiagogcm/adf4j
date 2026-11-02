package dev.nthings.adf4j.ast;

import java.util.List;

/// An {@link Extension} that wraps a single block body: the macro's own `content` blocks, which
/// the engine renders as normal even when a custom renderer handles the header. The identity and
/// parameter components (`extensionType`/`extensionKey`/`text`/`macroParams`/`parameters`) carry
/// the same meaning as on {@link Extension}; an `ExtensionRenderer` here produces the header line
/// only. For multiple bodies see {@link MultiBodiedExtension}.
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
