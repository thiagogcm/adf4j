package dev.nthings.adf4j.ast;

import java.util.List;

/// The inline (in-the-flow-of-text) form of {@link Extension}, with the same identity and
/// parameter components (`extensionType`/`extensionKey`/`text`/`macroParams`/`parameters`), but
/// rendered within a line rather than as its own block. Custom rendering goes through the same
/// `ExtensionRenderer` hook.
public record InlineExtension(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, AdfExtension, InlineContent {

  public InlineExtension {
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "inlineExtension";
  }
}
