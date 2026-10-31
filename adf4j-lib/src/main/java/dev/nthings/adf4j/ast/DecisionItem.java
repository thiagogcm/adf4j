package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `decisionItem`: one decision with inline `content`. `state` is the decision status
/// (e.g. `DECIDED`), or `null` when unspecified.
public record DecisionItem(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock {

  public DecisionItem {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "decisionItem";
  }

  public @Nullable String state() {
    return attrs.text("state");
  }
}
