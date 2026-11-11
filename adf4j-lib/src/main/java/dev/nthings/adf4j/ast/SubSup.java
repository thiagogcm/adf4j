package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Subscript/superscript text mark. `subSupType` (`sub` or `sup`) selects the HTML tag; an absent
/// or unknown value leaves the text unwrapped.
public record SubSup(Attributes attrs) implements TextMark {

  @Override
  public String type() {
    return "subsup";
  }

  public @Nullable String subSupType() {
    return attrs.text("type");
  }
}
