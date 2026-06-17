/// The immutable ADF abstract syntax tree: the sealed `AdfNode`/`AdfMark` hierarchies and
/// their attribute records. {@link NullMarked}; a record component whose
/// compact constructor normalizes `null` to a default ("", empty collection, `empty()`)
/// stays non-null and never returns `null`.
@NullMarked
package dev.nthings.adf4j.ast;

import org.jspecify.annotations.NullMarked;
