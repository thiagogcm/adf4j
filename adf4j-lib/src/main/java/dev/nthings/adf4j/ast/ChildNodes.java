package dev.nthings.adf4j.ast;

import java.util.List;

/// Filters a node's children down to the kind its schema expects, for the accessors that hide
/// off-schema children without dropping them from `content`.
final class ChildNodes {

  private ChildNodes() {}

  static <T> List<T> ofType(List<? extends AdfNode> content, Class<T> type) {
    return content.stream().filter(type::isInstance).map(type::cast).toList();
  }
}
