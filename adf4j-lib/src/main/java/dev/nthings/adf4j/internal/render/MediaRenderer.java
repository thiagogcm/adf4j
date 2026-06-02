package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.Optional;

import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaAttrs;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;

final class MediaRenderer {

  String renderMediaSingle(MediaSingle node, RendererState context, AdfRenderer adfRenderer) {
    if (node.content().isEmpty()) {
      return "";
    }

    var blocks = new ArrayList<String>();
    for (var item : node.content()) {
      if (item instanceof Media media) {
        blocks.add(renderMedia(media, adfRenderer));
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
    // A caption renders as its own block at column 0, so its first inline is at a block start.
    return adfRenderer.renderInlineNodes(node.content(), context, true);
  }

  String renderMediaGroup(MediaGroup node, AdfRenderer adfRenderer) {
    if (node.content().isEmpty()) {
      return "";
    }

    var lines = new ArrayList<String>();
    for (var item : node.content()) {
      if (item instanceof Media media) {
        lines.add(renderMedia(media, adfRenderer));
      }
    }
    return String.join("\n", lines);
  }

  String renderMedia(Media node, AdfRenderer adfRenderer) {
    var rendered = renderMediaBlock(node.attrs());
    return adfRenderer.applyMarks(rendered, node.marks());
  }

  String renderMediaInline(MediaInline node, AdfRenderer adfRenderer) {
    var rendered = renderMediaBlock(node.attrs());
    return adfRenderer.applyMarks(rendered, node.marks());
  }

  private String renderMediaBlock(MediaAttrs attrs) {
    var details = mediaDetails(attrs);
    var attributeSuffix = renderImageAttributeSuffix(details.width(), details.height());
    return "![%s](%s)%s"
        .formatted(
            MarkdownText.escapeAltText(details.safeAlt()),
            MarkdownText.escapeUrlDestination(details.markdownSource()),
            attributeSuffix);
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
