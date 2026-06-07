package dev.nthings.adf4j.options;

/**
 * Which macro a {@link PageTreeResolver} is expanding. Their defaults differ — {@link #CHILDREN}
 * lists immediate children (the whole subtree only with {@code all=true} or a {@code depth}), whereas
 * {@link #PAGETREE} lists the whole tree — so a resolver typically reads the request parameters
 * differently per macro.
 */
public enum PageTreeMacro {

  /** The {@code pagetree} macro: a hierarchical tree of descendant pages. */
  PAGETREE,

  /** The {@code children} macro: the child pages of a page (immediate children by default). */
  CHILDREN
}
