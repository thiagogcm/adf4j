package dev.nthings.adf4j.model;

import java.util.ArrayList;

public record BlockStyles(String alignment, Integer indentationLevel, String fontSize) {

  public static BlockStyles none() {
    return new BlockStyles(null, null, null);
  }

  public boolean hasStyles() {
    return !toInlineCss().isBlank();
  }

  public String toInlineCss() {
    var styles = new ArrayList<String>(3);
    if (alignment != null && !alignment.isBlank()) {
      styles.add("text-align:" + alignment);
    }

    if (indentationLevel != null && indentationLevel > 0) {
      styles.add("margin-left:" + (indentationLevel * 2) + "em");
    }

    if (fontSize != null && !fontSize.isBlank()) {
      styles.add("font-size:" + fontSize);
    }

    return String.join("; ", styles);
  }
}
