package dev.nthings.adf4j.ast;

import java.util.List;

/// Root of the ADF AST: a document, block, or inline. {@link AdfCard}, {@link AdfExtension} and
/// {@link AdfMedia} are views across blocks and inlines, for the nodes that share accessors.
/// The sealed hierarchies grow as ADF evolves, so a consumer `switch` should keep a `default`
/// (or handle the `Unknown*` variants) rather than rely on exhaustiveness.
///
/// Each node mirrors its JSON object: `type`, the complete `attrs`, `content` in source order and
/// `marks`. Typed accessors such as {@link Heading#level()} are lenient views over `attrs`, so
/// reading never changes what a document writes back. See {@code AdfJson}.
public sealed interface AdfNode
    permits AdfDocument, AdfBlock, AdfInline, AdfCard, AdfExtension, AdfMedia {

  /// The ADF `type` this node reads from and writes to.
  String type();

  Attributes attrs();

  /// Children in source order, including any the schema would not allow. Empty for a leaf.
  default List<? extends AdfNode> content() {
    return List.of();
  }

  /// Marks, in source order.
  default List<AdfMark> marks() {
    return List.of();
  }
}
