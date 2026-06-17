package dev.nthings.adf4j.metadata;

import dev.nthings.adf4j.ast.AdfBlock;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// One Confluence `excerpt` macro region defined on a page: the marked content other pages can
/// embed via `excerpt-include`. `name` is the named-excerpt identifier (`name`
/// parameter), or `null` for the page's unnamed excerpt. `content` is the marked region's
/// parsed ADF blocks, which an `ExcerptResolver` implementation can keep per source page and
/// render with `convert(new AdfDocument(1, definition.content()), options)` when another
/// page includes it.
public record ExcerptDefinition(@Nullable String name, List<AdfBlock> content) {

  public ExcerptDefinition {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
