package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Anchor;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Attachments;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Chart;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Excerpt;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.ExcerptInclude;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Iframe;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.InlineMediaImage;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.LegacyChart;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Other;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.PageTree;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.Toc;
import dev.nthings.adf4j.internal.analyze.ConfluenceMacro.ViewPdf;
import dev.nthings.adf4j.internal.analyze.TocLevelRange;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.options.ExtensionOutput;
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MacroRenderer {

  private static final Logger log = LoggerFactory.getLogger(MacroRenderer.class);

  private final MediaRenderer mediaRenderer;
  private final BlockRenderer blockRenderer;

  MacroRenderer(MediaRenderer mediaRenderer, BlockRenderer blockRenderer) {
    this.mediaRenderer = mediaRenderer;
    this.blockRenderer = blockRenderer;
  }

  // `atLineStart`: true for block output, whose fallback text sits at column 0.
  String renderExtension(AdfExtension node, RenderContext context, boolean atLineStart) {
    var custom = context.session().resolvers().extension(node, context.path());
    if (custom != null) {
      return custom.markdown();
    }
    return switch (ConfluenceMacro.of(node)) {
      // Media in disguise: same resolver and placeholder semantics.
      case InlineMediaImage(var media) -> mediaRenderer.renderMedia(media, context);
      // Data lives elsewhere in the document, so a caption loses nothing.
      case Chart _ -> chartCaption(node, context);
      case PageTree(var reference) -> renderPageTreeMacro(reference, context);
      case Toc(var range) -> renderTocMacro(range, context);
      case Anchor(var id) -> HtmlFragments.anchor(id);
      case Iframe(var src) -> renderIframeMacro(src, context);
      case ViewPdf _ -> renderViewPdfMacro(node.macroParams(), context);
      case LegacyChart(var labelled) ->
          labelled
              ? renderChartMacro(node.macroParams(), context)
              : extensionFallback(node, context, atLineStart);
      case ExcerptInclude(var reference) -> renderExcerptInclude(reference, context);
      case Attachments _ -> renderAttachmentsMacro(node, context, atLineStart);
      case Excerpt _, Other _ -> extensionFallback(node, context, atLineStart);
    };
  }

  String renderBodiedExtension(BodiedExtension node, RenderContext context) {
    return switch (ConfluenceMacro.of(node)) {
      // Excerpt is transparent: only its body renders.
      case Excerpt _ -> headerThenBodies(node, context, (_, _) -> "");
      // Keep the body's data table as a caption, not a placeholder.
      case Chart _, LegacyChart _ -> headerThenBodies(node, context, this::chartCaption);
      case InlineMediaImage _,
          PageTree _,
          Toc _,
          Anchor _,
          Iframe _,
          ViewPdf _,
          ExcerptInclude _,
          Attachments _,
          Other _ ->
          headerThenBodies(node, context, this::blockFallback);
    };
  }

  String renderMultiBodiedExtension(MultiBodiedExtension node, RenderContext context) {
    // Schema predates this node; salvage the frame bodies.
    return headerThenBodies(node, context, this::blockFallback);
  }

  // Header (custom, else default), then body blocks.
  private String headerThenBodies(
      AdfExtension node,
      RenderContext context,
      BiFunction<AdfExtension, RenderContext, String> defaultHeader) {
    var custom = context.session().resolvers().extension(node, context.path());
    if (custom != null && custom.mode() == ExtensionOutput.Mode.REPLACE) {
      return custom.markdown();
    }
    var header = custom != null ? custom.markdown() : defaultHeader.apply(node, context);
    return MarkdownBlocks.join(List.of(header, blockRenderer.renderBlocks(node, context)));
  }

  private String blockFallback(AdfExtension node, RenderContext context) {
    return extensionFallback(node, context, true);
  }

  private String extensionFallback(AdfExtension node, RenderContext context, boolean atLineStart) {
    var text = node.text();
    if (text != null && !text.isBlank()) {
      // Attribute text escapes like literal text.
      return context.session().writer().text(text, atLineStart);
    }
    var firstInRender = context.session().events().unsupportedExtension(node, context.path());
    return renderExtensionPlaceholder(node, firstInRender, context);
  }

  String renderSyncBlock(SyncBlock node, RenderContext context) {
    return syncBlockLabel(node.resourceId(), context);
  }

  String renderBodiedSyncBlock(BodiedSyncBlock node, RenderContext context) {
    var label = syncBlockLabel(node.resourceId(), context);
    return MarkdownBlocks.join(List.of(label, blockRenderer.renderBlocks(node, context)));
  }

  private String syncBlockLabel(@Nullable String resourceId, RenderContext context) {
    var label =
        resourceId == null || resourceId.isBlank() ? "Sync block" : "Sync block: " + resourceId;
    return context.session().writer().label(label);
  }

  // Resolver expansion, else the placeholder token (recorded as unresolved).
  private String renderPageTreeMacro(PageTreeReference reference, RenderContext context) {
    var fallback =
        switch (reference.macro()) {
          case CHILDREN -> {
            var depth = reference.depth();
            yield reference.all() || depth.isEmpty()
                ? "{{children}}"
                : "{{children:" + depth.getAsInt() + "}}";
          }
          case PAGETREE -> {
            var root = pageTreeRoot(reference.root());
            yield root == null ? "{{pagetree}}" : "{{pagetree:" + root + "}}";
          }
        };
    var expanded =
        context
            .session()
            .events()
            .resolve(
                () -> expandPageTree(reference, context),
                UnresolvedResource.Kind.PAGE_TREE,
                reference.root(),
                context.path(),
                fallback,
                "Page tree unresolved");
    return expanded != null ? expanded : fallback;
  }

  // Entries as an indented bullet list (empty when the answer is empty). Null falls back to the
  // token: no resolver, null return, or throw.
  private @Nullable String expandPageTree(PageTreeReference reference, RenderContext context) {
    var entries = context.session().resources().pageTree(reference, context.path());
    if (entries == null) {
      return null;
    }

    // Shallowest entry at column 0, so a deeper root doesn't over-indent into code.
    var baseDepth = entries.stream().mapToInt(PageTreeEntry::depth).min().orElse(0);
    var lines = new ArrayList<String>();
    for (var entry : entries) {
      var label = pageTreeLabel(entry, context);
      if (label != null) {
        lines.add(MarkdownBlocks.LIST_INDENT.repeat(entry.depth() - baseDepth) + "- " + label);
      }
    }
    return String.join("\n", lines);
  }

  // Link when the page id resolves, else the escaped one-line title; null if nothing renders.
  private @Nullable String pageTreeLabel(PageTreeEntry entry, RenderContext context) {
    var label = Strings.collapseWhitespace(entry.title()).strip();
    var pageNodeId = entry.pageNodeId();
    var href =
        pageNodeId == null || pageNodeId.isBlank()
            ? null
            : context
                .session()
                .events()
                .resolve(
                    () -> context.session().resources().pageId(pageNodeId, context.path()),
                    UnresolvedResource.Kind.PAGE,
                    pageNodeId,
                    context.path(),
                    label,
                    null);
    if (href != null) {
      return context.session().writer().link(label.isEmpty() ? href : label, href);
    }
    return label.isEmpty() ? null : context.session().writer().text(label, false);
  }

  // Root for the {{pagetree:<root>}} token: whitespace collapsed, braces dropped.
  private @Nullable String pageTreeRoot(@Nullable String root) {
    if (root == null) {
      return null;
    }
    var flattened = Strings.collapseWhitespace(root).replace("{", "").replace("}", "").strip();
    return flattened.isEmpty() ? null : flattened;
  }

  // Resolver expansion, else the labelled placeholder (recorded as unresolved).
  private String renderExcerptInclude(ExcerptIncludeReference reference, RenderContext context) {
    var label =
        reference.excerptName() == null
            ? "Excerpt include: " + reference.page()
            : "Excerpt include: " + reference.page() + " / " + reference.excerptName();
    var fallback = context.session().writer().label(label);
    var resolved =
        context
            .session()
            .events()
            .resolve(
                () -> context.session().resolvers().excerpt(reference, context.path()),
                UnresolvedResource.Kind.EXCERPT,
                reference.page(),
                context.path(),
                fallback,
                "Excerpt unresolved");
    return resolved != null ? resolved : fallback;
  }

  // Inventory as a bullet list of links ("" when authoritatively empty); only a missing inventory
  // keeps the placeholder. Untitled entries are labelled by file id.
  private String renderAttachmentsMacro(
      AdfExtension node, RenderContext context, boolean atLineStart) {
    var attachments = context.session().attachments();
    if (!attachments.isKnown()) {
      var fallback = extensionFallback(node, context, atLineStart);
      context
          .session()
          .events()
          .unresolved(
              UnresolvedResource.Kind.ATTACHMENT_INVENTORY,
              null,
              context.path(),
              UnresolvedResource.Reason.MISSING_INVENTORY,
              fallback,
              null);
      return fallback;
    }
    var writer = context.session().writer();
    var lines = new ArrayList<String>();
    for (var reference : attachments.entries()) {
      var title = reference.title();
      var label = title == null || title.isBlank() ? reference.fileId() : title;
      lines.add("- " + writer.link(label, resolveAttachment(reference, context)));
    }
    return String.join("\n", lines);
  }

  private String renderTocMacro(TocLevelRange range, RenderContext context) {
    var filtered =
        context.session().headingOutline().headings().stream()
            .filter(heading -> range.includes(heading.level()))
            .toList();

    var baseLevel = filtered.stream().mapToInt(HeadingReference::level).min().orElse(1);
    var lines = new ArrayList<String>();
    for (var heading : filtered) {
      lines.add(
          MarkdownBlocks.LIST_INDENT.repeat(heading.level() - baseLevel)
              + "- "
              + context.session().writer().link(heading.text(), "#" + heading.anchor()));
    }
    return String.join("\n", lines);
  }

  private String renderIframeMacro(@Nullable String src, RenderContext context) {
    if (src == null || src.isBlank()) {
      return context.session().writer().label("Embedded content");
    }
    return context.session().writer().link("Embedded content", src);
  }

  private String renderViewPdfMacro(MacroParams macroParams, RenderContext context) {
    var name = macroParams.value("name");
    var label = name == null || name.isBlank() ? "PDF" : "PDF: " + name;
    var attachmentReference =
        AttachmentReferences.resolve(macroParams, context.session().attachments());
    var writer = context.session().writer();
    if (attachmentReference == null) {
      var fallback = writer.label(label);
      context
          .session()
          .events()
          .unresolved(
              UnresolvedResource.Kind.ATTACHMENT,
              name,
              context.path(),
              context.session().attachments().isKnown()
                  ? UnresolvedResource.Reason.NO_ANSWER
                  : UnresolvedResource.Reason.MISSING_INVENTORY,
              fallback,
              "Attachment unavailable");
      return fallback;
    }
    return writer.link(label, resolveAttachment(attachmentReference, context));
  }

  // Attachment resolver, then file resolver, then the reference's downloadUrl; else the
  // attachment:<fileId> placeholder.
  private String resolveAttachment(AttachmentReference reference, RenderContext context) {
    var fallback = "attachment:" + reference.fileId();
    var resolved =
        context
            .session()
            .events()
            .resolve(
                () -> context.session().resources().attachmentSource(reference, context.path()),
                UnresolvedResource.Kind.ATTACHMENT,
                reference.fileId(),
                context.path(),
                fallback,
                "Attachment URL unresolved");
    return resolved != null ? resolved : fallback;
  }

  // Bodyless legacy chart: nothing recoverable, so a labelled placeholder.
  private String renderChartMacro(MacroParams macroParams, RenderContext context) {
    return context.session().writer().label(chartLabel(macroParams.value("title")));
  }

  // The italic "Chart: <title>" caption.
  private String chartCaption(AdfExtension node, RenderContext context) {
    var title = ConfluenceSupport.chartTitle(node.macroParams(), node.parameters());
    return "*" + context.session().writer().text(chartLabel(title), false) + "*";
  }

  // One line, so a label token or caption emphasis survives any title.
  private static String chartLabel(@Nullable String title) {
    var oneLine = title == null ? "" : Strings.collapseLineBreaks(title).strip();
    return oneLine.isEmpty() ? "Chart" : "Chart: " + oneLine;
  }

  // One WARN per extension type/key per render; repeats log at DEBUG.
  private String renderExtensionPlaceholder(
      AdfExtension node, boolean firstInRender, RenderContext context) {
    var label = AdfText.extensionLabel(node.extensionType(), node.extensionKey());
    if (firstInRender) {
      log.warn(
          "Rendering placeholder for unsupported extension: {} (logged once per render; "
              + "further occurrences log at DEBUG, per-document detail is on diagnostics)",
          label);
    } else {
      log.debug("Rendering placeholder for unsupported extension: {}", label);
    }
    return context.session().writer().label(AdfText.extensionPlaceholder(node));
  }
}
