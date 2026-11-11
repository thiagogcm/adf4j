/// The immutable ADF abstract syntax tree. Each record mirrors one JSON node with its full `attrs`,
/// `content` and `marks`, so a parsed document writes back unchanged. Accessors are lenient views
/// over `attrs`. The sealed `AdfMedia`, `AdfCard`, `AdfExtension` and `AdfUnknown` interfaces share
/// accessors across nodes. {@link Adf} builds only the node types the sealed `*Content` interfaces
/// allow.
@NullMarked
package dev.nthings.adf4j.ast;

import org.jspecify.annotations.NullMarked;
