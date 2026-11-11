package dev.nthings.adf4j.ast;

import java.util.ArrayList;
import java.util.List;

/// The `doc` root: the top-level blocks of an ADF document, in order. `version` is the ADF schema
/// version the source declared; it is carried through unchanged and does not gate parsing.
public record AdfDocument(int version, List<AdfBlock> content) implements AdfNode {

  public AdfDocument {
    content = List.copyOf(content);
  }

  @Override
  public String type() {
    return "doc";
  }

  /// Returns a copy with `blocks` appended, filling in new nodes' `localId`s as
  /// {@link Adf#doc(List)} does.
  public AdfDocument append(DocContent... blocks) {
    return append(List.of(blocks));
  }

  public AdfDocument append(List<? extends DocContent> blocks) {
    var appended = new ArrayList<>(content);
    appended.addAll(Adf.withLocalIds(content, blocks));
    return new AdfDocument(version, appended);
  }

  /// Always empty: ADF defines no attributes on the root.
  @Override
  public Attributes attrs() {
    return Attributes.empty();
  }
}
