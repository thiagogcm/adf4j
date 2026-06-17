package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// A status lozenge (the coloured pill). `text` is the label and the only rendered part (it falls
/// back to `"status"` when blank); `color` is the lozenge colour (e.g. `neutral`, `green`,
/// `yellow`, `red`, `blue`) and `style` its visual variant (e.g. `""`/`bold`). Both are
/// visual-only and dropped from Markdown. `localId` is editor-local.
public record Status(
    @Nullable String text, @Nullable String color, @Nullable String style, @Nullable String localId)
    implements AdfInline {}
