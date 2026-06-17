package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A `decisionItem`: one decision with inline `content`. `state` is the decision status
/// (e.g. `DECIDED`), or `null` when unspecified.
public record DecisionItem(@Nullable String state, List<AdfInline> content) implements AdfBlock {

  public DecisionItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
