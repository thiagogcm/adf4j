package dev.nthings.adf4j.ast;

/// Marks the ADF schema allows on {@link Text}. The schema also limits `code` to `link` and
/// `annotation`; not enforced here.
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
