package dev.nthings.adf4j.renderer;

import java.util.ArrayList;
import java.util.Optional;

import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaAttrs;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;

public final class MediaRenderer {

  private static final int DEFAULT_INLINE_IMAGE_HEIGHT_PX = 22;

  String renderMediaSingle(MediaSingle node, RendererState context, AdfRenderer adfRenderer) {
    if (node.content().isEmpty()) {
      return "";
    }

    var blocks = new ArrayList<String>();
    for (var item : node.content()) {
      if (item instanceof Media media) {
        blocks.add(
            context.strategy().isStorage()
                ? renderMedia(media, context, adfRenderer)
                : renderPresentationMediaSingle(media.attrs(), node));
      } else if (item instanceof Caption caption) {
        var rendered = renderCaption(caption, context, adfRenderer);
        if (!rendered.isBlank()) {
          blocks.add(rendered);
        }
      }
    }

    return String.join("\n\n", blocks);
  }

  String renderCaption(Caption node, RendererState context, AdfRenderer adfRenderer) {
    return adfRenderer.renderInlineNodes(node.content(), context);
  }

  String renderMediaGroup(MediaGroup node, RendererState context, AdfRenderer adfRenderer) {
    if (node.content().isEmpty()) {
      return "";
    }

    var lines = new ArrayList<String>();
    for (var item : node.content()) {
      if (item instanceof Media media) {
        lines.add(renderMedia(media, context, adfRenderer));
      }
    }
    return String.join("\n", lines);
  }

  String renderMedia(Media node, RendererState context, AdfRenderer adfRenderer) {
    var rendered = renderMediaBlock(node.attrs());
    return adfRenderer.applyMarks(rendered, node.marks(), context);
  }

  String renderMediaInline(
      RenderingStrategy strategy, MediaInline node, RendererState context, AdfRenderer adfRenderer) {
    var rendered = strategy.usesStyledInlineMedia()
        ? renderStyledInlineMedia(node.attrs())
        : renderMediaBlock(node.attrs());
    return adfRenderer.applyMarks(rendered, node.marks(), context);
  }

  private String renderPresentationMediaSingle(MediaAttrs attrs, MediaSingle mediaSingle) {
    var details = mediaDetails(attrs);
    if (details.source().isEmpty()) {
      return renderMediaBlock(attrs);
    }

    var layout = mediaSingle.layout();
    var style = renderMediaSingleStyle(mediaSingle, layout);
    return HtmlFragments.image(
        details.source().orElseThrow(),
        details.safeAlt(),
        image -> {
          if (details.width() != null) {
            image.attr("width", Integer.toString(details.width()));
          }
          if (details.height() != null) {
            image.attr("height", Integer.toString(details.height()));
          }
          if (layout != null && !layout.isBlank()) {
            image.attr("data-layout", layout);
          }
          if (style != null && !style.isBlank()) {
            image.attr("style", style);
          }
        });
  }

  private String renderMediaBlock(MediaAttrs attrs) {
    var details = mediaDetails(attrs);
    var attributeSuffix = renderImageAttributeSuffix(details.width(), details.height());
    return "![%s](%s)%s"
        .formatted(details.safeAlt(), details.markdownSource(), attributeSuffix);
  }

  private String renderStyledInlineMedia(MediaAttrs attrs) {
    var details = mediaDetails(attrs);
    if (details.source().isEmpty()) {
      return renderMediaBlock(attrs);
    }

    var inlineHeight = resolveInlineImageHeight(details.height());
    var inlineWidth = resolveInlineImageWidth(details.width(), details.height(), inlineHeight);
    return HtmlFragments.image(
        details.source().orElseThrow(),
        details.safeAlt(),
        image -> {
          if (inlineWidth != null) {
            image.attr("width", Integer.toString(inlineWidth));
          }
          if (inlineHeight != null) {
            image.attr("height", Integer.toString(inlineHeight));
          }
        });
  }

  private String resolveMediaSource(MediaAttrs attrs) {
    var url = attrs.url();
    if (url != null && !url.isBlank()) {
      return url;
    }

    var id = attrs.id();
    if (id == null || id.isBlank()) {
      return null;
    }

    var collection = attrs.collection();
    if (collection == null || collection.isBlank()) {
      return "media:%s".formatted(id);
    }
    return "media:%s/%s".formatted(collection, id);
  }

  private MediaDetails mediaDetails(MediaAttrs attrs) {
    var alt = attrs.alt();
    var safeAlt = alt == null || alt.isBlank() ? "media" : alt;
    return new MediaDetails(
        safeAlt,
        Optional.ofNullable(resolveMediaSource(attrs)),
        positiveInteger(attrs.width()),
        positiveInteger(attrs.height()));
  }

  private Integer resolveInlineImageHeight(Integer intrinsicHeight) {
    if (intrinsicHeight == null) {
      return DEFAULT_INLINE_IMAGE_HEIGHT_PX;
    }
    return Math.min(intrinsicHeight, DEFAULT_INLINE_IMAGE_HEIGHT_PX);
  }

  private Integer resolveInlineImageWidth(
      Integer intrinsicWidth, Integer intrinsicHeight, Integer targetHeight) {
    if (targetHeight == null || targetHeight <= 0) {
      return null;
    }
    if (intrinsicWidth == null || intrinsicHeight == null || intrinsicHeight <= 0) {
      return null;
    }
    return Math.max(1, Math.round((float) intrinsicWidth * targetHeight / intrinsicHeight));
  }

  private String renderImageAttributeSuffix(Integer width, Integer height) {
    var attributes = new ArrayList<String>();
    if (width != null) {
      attributes.add("width=%d".formatted(width));
    }
    if (height != null) {
      attributes.add("height=%d".formatted(height));
    }
    if (attributes.isEmpty()) {
      return "";
    }
    return "{%s}".formatted(String.join(" ", attributes));
  }

  private String renderMediaSingleStyle(MediaSingle mediaSingle, String layout) {
    var styles = new ArrayList<String>();
    var widthType = mediaSingle.widthType();
    if ("wide".equals(layout) || "full-width".equals(layout)) {
      styles.add("width:100%");
      styles.add("height:auto");
    } else {
      var displayWidth = positiveInteger(mediaSingle.width());
      if (displayWidth != null) {
        if ("percentage".equals(widthType)) {
          styles.add("width:%d%%".formatted(displayWidth));
        } else {
          styles.add("width:min(100%%, %dpx)".formatted(displayWidth));
        }
        styles.add("height:auto");
      }
    }

    if (layout == null || layout.isBlank()) {
      return styles.isEmpty() ? null : String.join(";", styles);
    }

    switch (layout) {
      case "center" -> {
        styles.add("display:block");
        styles.add("margin:0 auto");
      }
      case "align-end" -> {
        styles.add("display:block");
        styles.add("margin-left:auto");
      }
      case "wrap-left" -> {
        styles.add("float:left");
        styles.add("margin:0 1rem 1rem 0");
      }
      case "wrap-right" -> {
        styles.add("float:right");
        styles.add("margin:0 0 1rem 1rem");
      }
      default -> {
      }
    }

    return styles.isEmpty() ? null : String.join(";", styles);
  }

  private Integer positiveInteger(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }
    try {
      var parsed = Math.round(Float.parseFloat(rawValue));
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException _) {
      return null;
    }
  }

  private record MediaDetails(
      String safeAlt, Optional<String> source, Integer width, Integer height) {

    private MediaDetails {
      source = source == null ? Optional.empty() : source;
    }

    private String markdownSource() {
      return source.orElse("media");
    }
  }
}
