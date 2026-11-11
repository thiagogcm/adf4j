package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfMedia;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;

final class MediaRenderer {

  private final TextMarkRenderer markRenderer;
  private final BlockRenderer blockRenderer;

  MediaRenderer(TextMarkRenderer markRenderer, BlockRenderer blockRenderer) {
    this.markRenderer = markRenderer;
    this.blockRenderer = blockRenderer;
  }

  String renderMediaSingle(MediaSingle node, RenderContext context) {
    // A link can't span the blank line between images, so each wrapper covers one image.
    var blocks = new ArrayList<String>();
    for (var media : context.children(node, Media.class)) {
      var image = renderMedia(media.node(), context.at(media));
      blocks.add(markRenderer.applyMarks(image, node.marks(), context));
    }
    for (var caption : context.children(node, Caption.class)) {
      var rendered = blockRenderer.renderCaption(caption.node(), context.at(caption));
      if (!rendered.isBlank()) {
        blocks.add(rendered);
      }
    }
    return String.join("\n\n", blocks);
  }

  String renderMediaGroup(MediaGroup node, RenderContext context) {
    // Single newline: one visual cluster.
    return String.join(
        "\n",
        context.children(node, Media.class).stream()
            .map(media -> renderMedia(media.node(), context.at(media)))
            .toList());
  }

  String renderMedia(AdfMedia node, RenderContext context) {
    context.session().events().observe(node, context.path());
    var rendered = renderEmbedOrLink(node, context);
    return markRenderer.applyMarks(rendered, node.marks(), context);
  }

  private String renderEmbedOrLink(AdfMedia media, RenderContext context) {
    var session = context.session();
    var attachment = session.attachments().byFileId(media.id());
    var callbacks = session.events().callbacks();
    var failures = callbacks.failureCount();
    var resolvedSource = session.resources().mediaSource(media, context.path());
    var destination = resolvedSource != null ? resolvedSource : placeholder(media);
    if (resolvedSource == null) {
      session
          .events()
          .unresolved(
              UnresolvedResource.Kind.MEDIA,
              media.id(),
              context.path(),
              callbacks.reasonSince(failures),
              destination,
              "Media URL unresolved");
    }

    // Non-images (PDF, video, archive, …) become links; an image embed would break.
    if (!isImage(media, attachment, resolvedSource)) {
      // Inventory title first: a downloadUrl's last segment may be "download".
      var destinationFileName =
          attachment != null && notBlank(attachment.title())
              ? attachment.title()
              : AttachmentReferences.fileName(resolvedSource);
      return session.writer().link(fileLabel(media, destinationFileName), destination);
    }

    // {width= height=} is non-GFM: opt-in only.
    var source = MarkdownText.escapeUrlDestination(destination);
    var attributeSuffix =
        session.options().imageSizeAttributes()
            ? renderImageAttributeSuffix(
                positiveInteger(media.width()), positiveInteger(media.height()))
            : "";
    return "![%s](%s)%s"
        .formatted(session.writer().alt(imageAlt(media, attachment)), source, attributeSuffix);
  }

  // Name, file name, alt, then destination file name; else "file".
  private String fileLabel(AdfMedia media, @Nullable String destinationFileName) {
    var label =
        Strings.firstNonBlank(media.name(), media.fileName(), media.alt(), destinationFileName);
    return label == null ? "file" : label;
  }

  private String imageAlt(AdfMedia media, @Nullable AttachmentReference attachment) {
    var alt = AdfText.mediaAlt(media);
    if (!"media".equals(alt) || attachment == null) {
      return alt;
    }
    var title = attachment.title();
    return title == null || title.isBlank() ? alt : title;
  }

  // Type missing from the node (as in Confluence): fall back to the inventory or the extension.
  private boolean isImage(
      AdfMedia media, @Nullable AttachmentReference attachment, @Nullable String resolvedSource) {
    var mimeOrType = Strings.firstNonBlank(media.fileMimeType(), media.mediaType());
    if (mimeOrType == null && attachment != null) {
      mimeOrType = attachment.mediaType();
    }
    var attachmentTitle = attachment == null ? null : attachment.title();
    return AttachmentReferences.isImage(
        mimeOrType,
        media.fileName(),
        media.name(),
        media.alt(),
        attachmentTitle,
        media.url(),
        resolvedSource);
  }

  private static boolean notBlank(@Nullable String value) {
    return value != null && !value.isBlank();
  }

  // Synthetic media:collection/id reference; "media" when the id is absent.
  private String placeholder(AdfMedia media) {
    var id = media.id();
    if (id == null || id.isBlank()) {
      return "media";
    }

    var collection = media.collection();
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
