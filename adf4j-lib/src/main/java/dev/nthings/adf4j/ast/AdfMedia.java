package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// A media node, {@link Media} or {@link MediaInline}, with its fields read from `attrs`.
/// {@link #sourceType()} distinguishes `file`/`link` media (identified by {@link #id()} plus
/// {@link #collection()}, resolvable via a `MediaResolver`) from `external` media (a direct
/// {@link #url()}).
public sealed interface AdfMedia extends AdfNode permits Media, MediaInline {

  /// The ADF `type` attribute: `file`, `link` or `external`. Not to be confused with
  /// {@link AdfNode#type()}, the node's own type.
  default @Nullable String sourceType() {
    return attrs().text("type");
  }

  /// The media services file id.
  default @Nullable String id() {
    return attrs().text("id");
  }

  /// The editor's local id for this node: editor-local, so it never identifies a file.
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

  /// The alternative text.
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

  /// The coarse kind, such as `image` or `file`. See {@link #fileMimeType()} for the exact type.
  default @Nullable String mediaType() {
    return attrs().text("mediaType");
  }

  /// The exact MIME type, from the `__fileMimeType` attribute.
  default @Nullable String fileMimeType() {
    return attrs().text("__fileMimeType");
  }

  /// The file name, from the `__fileName` attribute.
  default @Nullable String fileName() {
    return attrs().text("__fileName");
  }

  /// The display name.
  default @Nullable String name() {
    return attrs().text("name");
  }
}
