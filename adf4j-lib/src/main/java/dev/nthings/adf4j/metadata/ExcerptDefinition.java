package dev.nthings.adf4j.metadata;

import java.util.List;

import dev.nthings.adf4j.ast.AdfBlock;

/**
 * One Confluence {@code excerpt} macro region defined on a page: the marked content other pages can
 * embed via {@code excerpt-include}. {@code name} is the named-excerpt identifier ({@code name}
 * parameter), or {@code null} for the page's unnamed excerpt. {@code content} is the marked region's
 * parsed ADF blocks — an {@code ExcerptResolver} implementation can keep these per source page and
 * render one with {@code convert(new AdfDocument(1, definition.content()), options)} when another
 * page includes it.
 */
public record ExcerptDefinition(String name, List<AdfBlock> content) {

  public ExcerptDefinition {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
