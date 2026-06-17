package dev.nthings.adf4j.ast;

/// A formatting mark decorating text or media. A separate hierarchy from {@link AdfNode}, since
/// marks wrap nodes rather than nest as children. An unrecognized mark parses as
/// {@link UnknownMark}. The permits list grows with ADF (see the {@link AdfNode} note).
public sealed interface AdfMark
    permits Strong,
        Em,
        Code,
        Strike,
        Underline,
        SubSup,
        Link,
        TextColor,
        BackgroundColor,
        Alignment,
        Indentation,
        FontSize,
        Border,
        Annotation,
        Breakout,
        Fragment,
        DataConsumer,
        UnknownMark {

  String type();
}
