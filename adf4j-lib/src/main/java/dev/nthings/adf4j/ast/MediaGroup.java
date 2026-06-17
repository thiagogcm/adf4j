package dev.nthings.adf4j.ast;

import java.util.List;

/// A cluster of attachments shown together: the outer wrapper around one or more {@link Media}
/// children, typically the non-image files (PDFs, archives, …) of a page. Unlike
/// {@link MediaSingle} it carries no layout or width and takes no `marks`; per-item attributes
/// live on each child's {@link MediaAttrs}.
public record MediaGroup(List<AdfBlock> content) implements AdfBlock {

  public MediaGroup {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
