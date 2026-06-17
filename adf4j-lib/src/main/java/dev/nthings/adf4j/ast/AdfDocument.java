package dev.nthings.adf4j.ast;

import java.util.List;

/// The `doc` root node: the top-level blocks of an ADF document in order. `version` is the ADF
/// schema version the source declared (`1` today); it is carried through unchanged and does not
/// gate parsing. `content` is the block sequence walked to render the document.
public record AdfDocument(int version, List<AdfBlock> content) implements AdfNode {

  public AdfDocument {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
