package dev.nthings.adf4j.ast;

public record MediaAttrs(
    String type,
    String id,
    String localId,
    String url,
    String collection,
    String alt,
    String width,
    String height,
    String mediaType,
    String fileMimeType,
    String fileName,
    String name) {

  public static Builder builder() {
    return new Builder();
  }

  /** The explicit MIME type if present, otherwise the coarser media type. */
  public String mimeOrType() {
    return firstNonBlank(fileMimeType, mediaType);
  }

  /** Alt text for an image embed: the description if given, else the file name, else {@code "media"}. */
  public String imageAlt() {
    var label = firstNonBlank(alt, fileName, name);
    return label == null ? "media" : label;
  }

  /** Label for a file link: the name, file name, alt, or destination's file name, else {@code "file"}. */
  public String fileLabel(String destinationFileName) {
    var label = firstNonBlank(name, fileName, alt, destinationFileName);
    return label == null ? "file" : label;
  }

  private static String firstNonBlank(String... values) {
    for (var value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  public static final class Builder {
    private String type;
    private String id;
    private String localId;
    private String url;
    private String collection;
    private String alt;
    private String width;
    private String height;
    private String mediaType;
    private String fileMimeType;
    private String fileName;
    private String name;

    private Builder() {
    }

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder localId(String localId) {
      this.localId = localId;
      return this;
    }

    public Builder url(String url) {
      this.url = url;
      return this;
    }

    public Builder collection(String collection) {
      this.collection = collection;
      return this;
    }

    public Builder alt(String alt) {
      this.alt = alt;
      return this;
    }

    public Builder width(String width) {
      this.width = width;
      return this;
    }

    public Builder height(String height) {
      this.height = height;
      return this;
    }

    public Builder mediaType(String mediaType) {
      this.mediaType = mediaType;
      return this;
    }

    public Builder fileMimeType(String fileMimeType) {
      this.fileMimeType = fileMimeType;
      return this;
    }

    public Builder fileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder name(String name) {
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
