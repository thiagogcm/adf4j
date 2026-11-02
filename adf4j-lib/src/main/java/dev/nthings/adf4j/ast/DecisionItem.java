package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `decisionItem`: one decision with inline `content`. `state` is the decision status
/// (e.g. `DECIDED`), or `null` when unspecified.
public record DecisionItem(Attributes attrs, List<AdfInline> content, List<AdfMark> marks)
    implements AdfBlock {

  public DecisionItem {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "decisionItem";
  }

  public @Nullable String state() {
    return attrs.text("state");
  }

  /// This node with a fixed `localId` in place of the generated one.
  public DecisionItem withLocalId(String localId) {
    return new DecisionItem(attrs.with("localId", localId), content, marks);
  }
}
