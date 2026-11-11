package dev.nthings.adf4j.metadata;

import dev.nthings.adf4j.ast.AdfBlock;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// A Confluence `excerpt` region on a page, embeddable by other pages via `excerpt-include`.
/// `name` is null for the unnamed excerpt. `content` is the region's parsed ADF blocks, renderable
/// with `AdfToMarkdown.convert(ExcerptDefinition)`.
public record ExcerptDefinition(@Nullable String name, List<AdfBlock> content) {

  public ExcerptDefinition {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
