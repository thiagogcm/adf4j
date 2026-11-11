package dev.nthings.adf4j.ast;

import java.util.List;

/// The inline form of {@link Extension}, with the same identity and parameter components, rendered
/// within a line. Custom rendering uses the same `ExtensionRenderer` hook.
public record InlineExtension(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, AdfExtension, InlineContent {

  public InlineExtension {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "inlineExtension";
  }
}
