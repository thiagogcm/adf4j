package dev.nthings.adf4j.ast;

public record ConfluenceMetadata(String linkType, String pageId, String contentId, String id) {

  private static final ConfluenceMetadata EMPTY = new ConfluenceMetadata(null, null, null, null);

  public static ConfluenceMetadata empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return linkType == null && pageId == null && contentId == null && id == null;
  }
}
