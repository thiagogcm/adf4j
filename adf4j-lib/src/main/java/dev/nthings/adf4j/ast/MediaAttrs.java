package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/**
 * The attributes of a media node. {@code type} distinguishes {@code file}/{@code link} media
 * (identified by {@code id}/{@code localId} + {@code collection}, resolvable via a
 * {@code MediaResolver}) from {@code external} media (a direct {@code url}). {@code width}/
 * {@code height} are pixel dimensions when present; {@code mediaType} is the coarse kind
 * ({@code image}, {@code file}, …) while {@code fileMimeType} is the exact MIME type; {@code alt},
 * {@code fileName} and {@code name} are display labels (see {@link #imageAlt()} and
 * {@link #fileLabel(String)} for the fallback order).
 */
public record MediaAttrs(
    @Nullable String type,
    @Nullable String id,
    @Nullable String localId,
    @Nullable String url,
    @Nullable String collection,
    @Nullable String alt,
    @Nullable String width,
    @Nullable String height,
    @Nullable String mediaType,
    @Nullable String fileMimeType,
    @Nullable String fileName,
    @Nullable String name) {

  public static Builder builder() {
    return new Builder();
  }

  /** The explicit MIME type if present, otherwise the coarser media type. */
  public @Nullable String mimeOrType() {
    return firstNonBlank(fileMimeType, mediaType);
  }

  /** Alt text for an image embed: the description if given, else the file name, else {@code "media"}. */
  public String imageAlt() {
    var label = firstNonBlank(alt, fileName, name);
    return label == null ? "media" : label;
  }

  /** Label for a file link: the name, file name, alt, or destination's file name, else {@code "file"}. */
  public String fileLabel(@Nullable String destinationFileName) {
    var label = firstNonBlank(name, fileName, alt, destinationFileName);
    return label == null ? "file" : label;
  }

  private static @Nullable String firstNonBlank(@Nullable String... values) {
    for (var value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  public static final class Builder {
    private @Nullable String type;
    private @Nullable String id;
    private @Nullable String localId;
    private @Nullable String url;
    private @Nullable String collection;
    private @Nullable String alt;
    private @Nullable String width;
    private @Nullable String height;
    private @Nullable String mediaType;
    private @Nullable String fileMimeType;
    private @Nullable String fileName;
    private @Nullable String name;

    private Builder() {
    }

    public Builder type(@Nullable String type) {
      this.type = type;
      return this;
    }

    public Builder id(@Nullable String id) {
      this.id = id;
      return this;
    }

    public Builder localId(@Nullable String localId) {
      this.localId = localId;
      return this;
    }

    public Builder url(@Nullable String url) {
      this.url = url;
      return this;
    }

    public Builder collection(@Nullable String collection) {
      this.collection = collection;
      return this;
    }

    public Builder alt(@Nullable String alt) {
      this.alt = alt;
      return this;
    }

    public Builder width(@Nullable String width) {
      this.width = width;
      return this;
    }

    public Builder height(@Nullable String height) {
      this.height = height;
      return this;
    }

    public Builder mediaType(@Nullable String mediaType) {
      this.mediaType = mediaType;
      return this;
    }

    public Builder fileMimeType(@Nullable String fileMimeType) {
      this.fileMimeType = fileMimeType;
      return this;
    }

    public Builder fileName(@Nullable String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder name(@Nullable String name) {
      this.name = name;
      return this;
    }

    public MediaAttrs build() {
      return new MediaAttrs(
          type, id, localId, url, collection, alt, width, height, mediaType, fileMimeType, fileName,
          name);
    }
  }
}
