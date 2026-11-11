package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// A media node, {@link Media} or {@link MediaInline}, with fields read from `attrs`. `file` and
/// `link` media are identified by {@link #id()} and {@link #collection()}, resolvable via a
/// `MediaResolver`; `external` media by {@link #url()}.
public sealed interface AdfMedia extends AdfNode permits Media, MediaInline {

  /// The `type` attribute (`file`, `link` or `external`), not {@link AdfNode#type()}.
  default @Nullable String sourceType() {
    return attrs().text("type");
  }

  /// The media services file id.
  default @Nullable String id() {
    return attrs().text("id");
  }

  /// Editor-local node id; it never identifies a file.
  default @Nullable String localId() {
    return attrs().text("localId");
  }

  /// The direct URL of `external` media.
  default @Nullable String url() {
    return attrs().text("url");
  }

  /// The media services collection holding the file.
  default @Nullable String collection() {
    return attrs().text("collection");
  }

  default @Nullable String alt() {
    return attrs().text("alt");
  }

  /// The pixel width, as text.
  default @Nullable String width() {
    return attrs().text("width");
  }

  /// The pixel height, as text.
  default @Nullable String height() {
    return attrs().text("height");
  }

  /// Coarse kind, such as `image` or `file`; see {@link #fileMimeType()} for the exact type.
  default @Nullable String mediaType() {
    return attrs().text("mediaType");
  }

  /// Exact MIME type, from `__fileMimeType`.
  default @Nullable String fileMimeType() {
    return attrs().text("__fileMimeType");
  }

  /// File name, from `__fileName`.
  default @Nullable String fileName() {
    return attrs().text("__fileName");
  }

  default @Nullable String name() {
    return attrs().text("name");
  }
}
