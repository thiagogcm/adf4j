package dev.nthings.adf4j.ast;

import java.util.List;

/// Root of the ADF AST: a document, a block, or an inline. {@link AdfCard}, {@link AdfExtension}
// and
/// {@link AdfMedia} are views across blocks and inlines, for the nodes that share accessors. The
/// sealed hierarchies under this root
/// *grow* as ADF evolves (new permitted types may be added in any release), so a consumer
/// `switch` should keep a `default` (or handle the `Unknown*` variants) rather than
/// rely on exhaustiveness over today's types.
///
/// Every node mirrors its JSON object: `type`, the complete `attrs`, its `content` in source order
/// and its `marks`. Typed accessors such as {@link Heading#level()} are lenient views over `attrs`,
/// so reading never changes what a document writes back. See {@code AdfJson}.
public sealed interface AdfNode
    permits AdfDocument, AdfBlock, AdfInline, AdfCard, AdfExtension, AdfMedia {

  /// The ADF `type` this node reads from and writes to.
  String type();

  /// The node's raw ADF attributes.
  Attributes attrs();

  /// The node's children in source order, including any the schema would not allow here. Empty for
  /// a leaf.
  default List<? extends AdfNode> content() {
    return List.of();
  }

  /// The node's marks in source order.
  default List<AdfMark> marks() {
    return List.of();
  }
}
