package dev.nthings.adf4j.metadata;

/// Which page-hierarchy macro a {@link PageTreeReference} stands for. Their defaults differ:
/// {@link #CHILDREN} lists immediate children (the whole subtree only with `all=true` or a
/// `depth`), whereas {@link #PAGETREE} lists the whole tree, so a resolver typically reads the
/// reference's parameters differently per macro.
public enum PageTreeMacro {

  /// The `pagetree` macro: a hierarchical tree of descendant pages.
  PAGETREE,

  /// The `children` macro: the child pages of a page (immediate children by default).
  CHILDREN
}
