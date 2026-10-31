package dev.nthings.adf4j.ast;

/// The node types the ADF schema allows directly inside `mediaSingle`. Records hold any child, so
/// that every parsed document fits; this names the types for code that constructs documents. It
/// covers node types only, not the marks, position, count or attributes a child also needs. The
/// schema gives a `mediaSingle` inside a `bodiedSyncBlock` no content rule; this applies the rule
/// that holds everywhere else, so it is stricter than the schema there. `AdfContentModelTests`
/// checks it against the schema.
public sealed interface MediaSingleContent extends AdfBlock permits Caption, Media {}
