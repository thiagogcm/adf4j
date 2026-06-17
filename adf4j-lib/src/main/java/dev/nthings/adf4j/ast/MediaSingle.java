package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A single piece of media laid out on its own: the outer wrapper around exactly one
/// {@link Media} child (plus an optional {@link Caption}). The actual file/external attributes
/// live on that child's {@link MediaAttrs}. `layout` is the editor placement
/// (e.g. `center`, `align-start`, `wide`, `full-width`); `width` with `widthType` is the editor
/// resize hint (e.g. a percentage when `widthType` is `percentage`). These wrapper layout fields
/// are not reflected in the Markdown output. `marks` on the wrapper decorate the whole embed, most
/// notably a {@link Link} making the image clickable.
public record MediaSingle(
    @Nullable String layout,
    @Nullable String widthType,
    @Nullable String width,
    List<AdfBlock> content,
    List<AdfMark> marks)
    implements AdfBlock {

  public MediaSingle {
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
