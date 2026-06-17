package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Subscript/superscript (`subsup`) text mark. `subSupType` selects which (`sub` or `sup`),
/// rendered as the matching `<sub>`/`<sup>` HTML tag; an absent or unrecognized value leaves the
/// text unwrapped.
public record SubSup(@Nullable String subSupType) implements AdfMark {

  @Override
  public String type() {
    return "subsup";
  }
}
