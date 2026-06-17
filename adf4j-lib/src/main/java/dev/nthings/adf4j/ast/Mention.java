package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// An `@`-mention of a user. `text` is the (stripped) display name and the preferred rendering;
/// when blank it falls back to `@`+`id` (then `localId`), else `@unknown`. `id` is the account id,
/// `userType` the kind of mentioned entity (e.g. `DEFAULT`, `SPECIAL`, `APP`), `accessLevel` the
/// mentioned user's container access (e.g. `CONTAINER`, `APPLICATION`). `localId` is editor-local.
public record Mention(
    @Nullable String id,
    String text,
    @Nullable String userType,
    @Nullable String accessLevel,
    @Nullable String localId)
    implements AdfInline {}
