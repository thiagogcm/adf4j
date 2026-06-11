package dev.nthings.adf4j.metadata;

/**
 * Which page-hierarchy macro a {@link PageTreeReference} stands for. Their defaults differ —
 * {@link #CHILDREN} lists immediate children (the whole subtree only with {@code all=true} or a
 * {@code depth}), whereas {@link #PAGETREE} lists the whole tree — so a resolver typically reads the
 * reference's parameters differently per macro.
 */
public enum PageTreeMacro {

  /** The {@code pagetree} macro: a hierarchical tree of descendant pages. */
  PAGETREE,

  /** The {@code children} macro: the child pages of a page (immediate children by default). */
  CHILDREN
}
