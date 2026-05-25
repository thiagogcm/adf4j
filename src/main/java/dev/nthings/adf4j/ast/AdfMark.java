package dev.nthings.adf4j.ast;

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
