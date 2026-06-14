/**
 * The immutable ADF abstract syntax tree: the sealed {@code AdfNode}/{@code AdfMark} hierarchies and
 * their attribute records. {@link org.jspecify.annotations.NullMarked}; a record component whose
 * compact constructor normalizes {@code null} to a default ("", empty collection, {@code empty()})
 * stays non-null and never returns {@code null}.
 */
@NullMarked
package dev.nthings.adf4j.ast;

import org.jspecify.annotations.NullMarked;
