package dev.nthings.adf4j.result;

/// A dependency missing from the output, with its emitted fallback and source JSON pointer.
public record UnresolvedResource(
    Kind kind, String id, String path, Reason reason, String fallback) {
  public enum Kind {
    MEDIA,
    ATTACHMENT,
    PAGE,
    PAGE_TREE,
    EXCERPT,
    ATTACHMENT_INVENTORY
  }

  public enum Reason {
    NO_ANSWER,
    MISSING_INVENTORY,
    CALLBACK_FAILED
  }
}
