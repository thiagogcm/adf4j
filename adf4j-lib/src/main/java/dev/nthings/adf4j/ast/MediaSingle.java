package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A single media item laid out alone: wraps exactly one {@link Media} and an optional {@link
/// Caption}. `layout` and `width`/`widthType` are editor hints not reflected in Markdown. `marks`
/// decorate the whole embed, e.g. a {@link Link} making it clickable.
public record MediaSingle(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BlockquoteContent,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        ListItemContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent {

  public MediaSingle {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "mediaSingle";
  }

  /// The {@link Media} children: exactly one in schema-shaped ADF; off-schema input can hold more.
  public List<Media> media() {
    return ChildNodes.ofType(content, Media.class);
  }

  /// The {@link Caption} children: at most one in schema-shaped ADF; off-schema input can hold
  /// more.
  public List<Caption> captions() {
    return ChildNodes.ofType(content, Caption.class);
  }

  public @Nullable String layout() {
    return attrs.text("layout");
  }

  public @Nullable String widthType() {
    return attrs.text("widthType");
  }

  public @Nullable String width() {
    return attrs.text("width");
  }

  public MediaSingle withLayout(Adf.MediaLayout layout) {
    return new MediaSingle(attrs.with("layout", layout.value()), content, marks);
  }

  /// `width` is a percentage of the page.
  public MediaSingle withWidth(double width) {
    return new MediaSingle(
        attrs.with("width", width).with("widthType", "percentage"), content, marks);
  }

  public MediaSingle withPixelWidth(int width) {
    return new MediaSingle(attrs.with("width", width).with("widthType", "pixel"), content, marks);
  }

  public MediaSingle withLink(Link link) {
    return new MediaSingle(attrs, content, List.of(link));
  }
}
