/// The immutable ADF abstract syntax tree: the sealed `AdfNode`/`AdfMark` hierarchies. A record
/// mirrors one JSON node, keeping its complete `attrs`, `content` and `marks`, so a parsed document
/// writes back unchanged. Accessors are lenient views over `attrs`; the sealed `AdfMedia`,
/// `AdfCard` and `AdfExtension` interfaces carry the accessors of the nodes that share them, and
/// `AdfUnknown` covers every unrecognized node and mark. Child lists are wide so that any parsed
/// document fits; the sealed `*Content` interfaces name the node types the schema allows in each
/// container, and {@link Adf} uses them to build documents the compiler checks. {@link NullMarked}.
@NullMarked
package dev.nthings.adf4j.ast;

import org.jspecify.annotations.NullMarked;
