package dev.nthings.adf4j.ast;

/// The marks the ADF schema allows on {@link Text}. `AdfContentModelTests` checks this against the
/// schema. The schema also limits `code` to `link` and `annotation`, which this doesn't cover.
public sealed interface TextMark extends AdfMark
    permits Annotation,
        BackgroundColor,
        Code,
        Em,
        Link,
        Strike,
        Strong,
        SubSup,
        TextColor,
        Underline {}
