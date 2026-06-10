package dev.nthings.adf4j.metadata;

import dev.nthings.adf4j.options.PageTreeMacro;

/**
 * One {@code pagetree} or {@code children} macro occurrence in a document. {@code root} is the page
 * the macro is rooted at (the {@code pagetree} {@code root} / {@code children} {@code page} parameter,
 * normalized as in {@link dev.nthings.adf4j.options.PageTreeRequest#root()}), or {@code null} for the
 * current page. A document with any of these depends on the page hierarchy, not just its own content.
 */
public record PageTreeReference(PageTreeMacro macro, String root) {}
