package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaAttrs;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.metadata.AttachmentReference;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;

final class MediaRenderer {

  String renderMediaSingle(MediaSingle node, RendererState context, BlockRecursion recursion) {
    if (node.content().isEmpty()) {
      return "";
    }

    // A mediaSingle-level link mark wraps the image only; any caption stays a separate block.
    var imageBlocks = new ArrayList<String>();
    var captionBlocks = new ArrayList<String>();
    for (var item : node.content()) {
      if (item instanceof Media media) {
        imageBlocks.add(renderMedia(media, context, recursion));
      } else if (item instanceof Caption caption) {
        var rendered = renderCaption(caption, context, recursion);
        if (!rendered.isBlank()) {
          captionBlocks.add(rendered);
        }
      }
    }

    var blocks = new ArrayList<String>();
    if (!imageBlocks.isEmpty()) {
      var imageString = String.join("\n\n", imageBlocks);
      blocks.add(recursion.applyMarks(imageString, node.marks(), context.context()));
    }
    blocks.addAll(captionBlocks);

    return String.join("\n\n", blocks);
  }

  String renderCaption(Caption node, RendererState context, BlockRecursion recursion) {
    // A caption renders as its own block at column 0, so its first inline is at a block start.
    return recursion.renderInlineNodes(node.content(), context, true);
  }

  String renderMediaGroup(MediaGroup node, RendererState context, BlockRecursion recursion) {
    if (node.content().isEmpty()) {
      return "";
    }

    var lines = new ArrayList<String>();
    for (var item : node.content()) {
      if (item instanceof Media media) {
        lines.add(renderMedia(media, context, recursion));
      }
    }
    // Single soft break: a media group renders as one visual cluster.
    return String.join("\n", lines);
  }

  String renderMedia(Media node, RendererState context, BlockRecursion recursion) {
    var rendered = renderMediaBlock(node.attrs(), context);
    return recursion.applyMarks(rendered, node.marks(), context.context());
  }

  String renderMediaInline(MediaInline node, RendererState context, BlockRecursion recursion) {
    var rendered = renderMediaBlock(node.attrs(), context);
    return recursion.applyMarks(rendered, node.marks(), context.context());
  }

  String renderMediaBlock(MediaAttrs attrs, RendererState context) {
    var attachment = context.context().attachmentByFileId(attrs.id());
    var resolvedSource = resolveMediaSource(attrs, attachment, context);
    var destination = resolvedSource != null ? resolvedSource : placeholder(attrs);

    // Non-image attachments (PDF, video, archive, …) render as a link; an image embed would break.
    if (!isImage(attrs, attachment, resolvedSource)) {
      // Prefer the inventory title: a downloadUrl's last segment may be a route word ("download").
      var destinationFileName =
          attachment != null && notBlank(attachment.title())
              ? attachment.title()
              : AttachmentReferences.fileName(resolvedSource);
      return MarkdownText.link(
          attrs.fileLabel(destinationFileName), destination, context.escapeParentheses());
    }

    // The {width= height=} suffix is non-GFM; emit only when opted in.
    var source = MarkdownText.escapeUrlDestination(destination);
    var attributeSuffix =
        context.imageSizeAttributes()
            ? renderImageAttributeSuffix(
                positiveInteger(attrs.width()), positiveInteger(attrs.height()))
            : "";
    return "![%s](%s)%s"
        .formatted(
            MarkdownText.escapeAltText(imageAlt(attrs, attachment), context.escapeParentheses()),
            source,
            attributeSuffix);
  }

  private String imageAlt(MediaAttrs attrs, @Nullable AttachmentReference attachment) {
    var alt = attrs.imageAlt();
    if (!"media".equals(alt) || attachment == null) {
      return alt;
    }
    var title = attachment.title();
    return title == null || title.isBlank() ? alt : title;
  }

  // Classifies a file node whose own attrs omit the type — as Confluence's do — via the
  // attachment inventory's mediaType/title or the resolved destination's filename extension.
  private boolean isImage(
      MediaAttrs attrs, @Nullable AttachmentReference attachment, @Nullable String resolvedSource) {
    var mimeOrType = attrs.mimeOrType();
    if ((mimeOrType == null || mimeOrType.isBlank()) && attachment != null) {
      mimeOrType = attachment.mediaType();
    }
    var attachmentTitle = attachment == null ? null : attachment.title();
    return AttachmentReferences.isImage(
        mimeOrType,
        attrs.fileName(),
        attrs.name(),
        attrs.alt(),
        attachmentTitle,
        attrs.url(),
        resolvedSource);
  }

  // A url attr, media resolver, or inventory downloadUrl, never the synthetic placeholder: null
  // so the label and image-ness are decided only by genuine filenames.
  private @Nullable String resolveMediaSource(
      MediaAttrs attrs, @Nullable AttachmentReference attachment, RendererState context) {
    var url = attrs.url();
    if (url != null && !url.isBlank()) {
      return url;
    }

    var resolver = context.mediaResolver();
    if (resolver != null) {
      var resolved = CallbackGuards.guardNonBlank("MediaResolver", () -> resolver.resolve(attrs));
      if (resolved != null) {
        return resolved;
      }
    }

    var downloadUrl = attachment == null ? null : attachment.downloadUrl();
    return downloadUrl == null || downloadUrl.isBlank() ? null : downloadUrl;
  }

  private static boolean notBlank(@Nullable String value) {
    return value != null && !value.isBlank();
  }

  // The synthetic media:collection/id reference, or "media" when even the id is absent.
  private String placeholder(MediaAttrs attrs) {
    var id = attrs.id();
    if (id == null || id.isBlank()) {
      return "media";
    }

    var collection = attrs.collection();
    if (collection == null || collection.isBlank()) {
      return "media:%s".formatted(id);
    }
    return "media:%s/%s".formatted(collection, id);
  }

  private String renderImageAttributeSuffix(@Nullable Integer width, @Nullable Integer height) {
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

  private @Nullable Integer positiveInteger(@Nullable String rawValue) {
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
}
