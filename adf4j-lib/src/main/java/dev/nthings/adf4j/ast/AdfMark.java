package dev.nthings.adf4j.ast;

/// A formatting mark on text or media. Separate from {@link AdfNode}, since marks wrap nodes rather
/// than nest as children. An unrecognized mark parses as {@link UnknownMark}. The permits list
/// grows with ADF (see {@link AdfNode}).
public sealed interface AdfMark
    permits TextMark, ParagraphMark, HeadingMark, MediaMark, Breakout, Fragment, UnknownMark {

  /// The ADF `type` this mark reads from and writes to.
  String type();

  Attributes attrs();
}
