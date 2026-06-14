package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.metadata.PageTreeReference;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.internal.analyze.TocLevelRange;
import dev.nthings.adf4j.options.ExtensionContext;
import dev.nthings.adf4j.options.PageTreeEntry;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MacroRenderer {

  private static final Logger log = LoggerFactory.getLogger(MacroRenderer.class);

  private final MediaRenderer mediaRenderer;
  // One WARN per extension type/key per converter; repeats log at DEBUG (diagnostics carry the
  // per-document detail).
  private final Set<String> placeholderWarned = ConcurrentHashMap.newKeySet();

  MacroRenderer(MediaRenderer mediaRenderer) {
    this.mediaRenderer = Objects.requireNonNull(mediaRenderer, "mediaRenderer");
  }

  String renderExtension(Extension node, RendererState context) {
    return renderExtensionCore(
        node.extensionType(), node.extensionKey(), node.text(), node.macroParams(),
        node.parameters(), context);
  }

  String renderInlineExtension(InlineExtension node, RendererState context) {
    return renderExtensionCore(
        node.extensionType(), node.extensionKey(), node.text(), node.macroParams(),
        node.parameters(), context);
  }

  private String renderExtensionCore(
      @Nullable String extensionType,
      @Nullable String extensionKey,
      @Nullable String text,
      MacroParams macroParams,
      Attributes parameters,
      RendererState context) {
    var custom = renderCustom(extensionType, extensionKey, text, macroParams, parameters, context);
    if (custom != null) {
      return custom;
    }
    if (ConfluenceSupport.isInlineMediaImage(extensionType, extensionKey)) {
      // A media node in disguise: the media path's resolver and placeholder semantics apply as-is.
      var mediaAttrs = ConfluenceSupport.inlineMediaImageAttrs(parameters);
      if (mediaAttrs != null) {
        return mediaRenderer.renderMediaBlock(mediaAttrs, context);
      }
    }
    if (ConfluenceSupport.isModernChartExtension(extensionType)) {
      // Its data table lives elsewhere in the same document, so a caption loses nothing —
      // Confluence's own export renders a sourceless chart as nothing at all.
      return chartCaption(macroParams, parameters, context);
    }
    if (!ConfluenceSupport.isConfluenceMacroExtension(extensionType)) {
      return extensionFallback(text, extensionType, extensionKey, context);
    }

    var rendered = switch (extensionKey != null ? extensionKey : "") {
      case "children", "pagetree" ->
          renderPageTreeMacro(ConfluenceSupport.pageTreeReference(extensionKey, macroParams), context);
      case "toc" -> renderTocMacro(macroParams, context);
      case "anchor" -> HtmlFragments.anchor(ConfluenceSupport.anchorId(macroParams));
      case "iframe" -> renderIframeMacro(macroParams, context);
      case "viewpdf" -> renderViewPdfMacro(macroParams, context);
      case "chart:default" -> renderChartMacro(macroParams, context);
      case "excerpt-include" ->
          renderExcerptInclude(
              ConfluenceSupport.excerptIncludeReference(extensionKey, macroParams), context);
      case "attachments" -> renderAttachmentsMacro(context);
      default -> null;
    };
    return rendered != null ? rendered : extensionFallback(text, extensionType, extensionKey, context);
  }

  List<String> renderBodiedExtension(
      BodiedExtension node, RendererState context, BlockRecursion recursion) {
    if (ConfluenceSupport.isConfluenceMacroExtension(node.extensionType())
        && "excerpt".equals(node.extensionKey())) {
      return recursion.renderBlocks(node.content(), context);
    }
    if (ConfluenceSupport.isChartExtension(node.extensionType(), node.extensionKey())) {
      // The body holds the data table; keep the numbers under a caption, not a placeholder.
      return captionThenBodies(node, context, recursion);
    }
    return headerThenBodies(
        node.text(), node.extensionType(), node.extensionKey(), node.macroParams(),
        node.parameters(), node.content(), context, recursion);
  }

  List<String> renderMultiBodiedExtension(
      MultiBodiedExtension node, RendererState context, BlockRecursion recursion) {
    // The schema predates this node; salvage the frame bodies.
    return headerThenBodies(
        node.text(), node.extensionType(), node.extensionKey(), node.macroParams(),
        node.parameters(), node.content(), context, recursion);
  }

  // Header (custom renderer, else macro text or "[Extension: …]" placeholder) then the body blocks.
  private List<String> headerThenBodies(
      @Nullable String text,
      @Nullable String extensionType,
      @Nullable String extensionKey,
      MacroParams macroParams,
      Attributes parameters,
      List<AdfBlock> content,
      RendererState context,
      BlockRecursion recursion) {
    var blocks = new ArrayList<String>();
    var custom = renderCustom(extensionType, extensionKey, text, macroParams, parameters, context);
    blocks.add(custom != null ? custom : extensionFallback(text, extensionType, extensionKey, context));
    blocks.addAll(recursion.renderBlocks(content, context));
    return blocks;
  }

  // Chart caption (custom renderer first) then the body blocks — no placeholder.
  private List<String> captionThenBodies(
      BodiedExtension node, RendererState context, BlockRecursion recursion) {
    var blocks = new ArrayList<String>();
    var custom = renderCustom(
        node.extensionType(), node.extensionKey(), node.text(), node.macroParams(),
        node.parameters(), context);
    blocks.add(custom != null ? custom : chartCaption(node.macroParams(), node.parameters(), context));
    blocks.addAll(recursion.renderBlocks(node.content(), context));
    return blocks;
  }

  // Custom renderers, consulted in order (first non-null wins); null defers to the next, then default.
  private @Nullable String renderCustom(
      @Nullable String extensionType,
      @Nullable String extensionKey,
      @Nullable String text,
      MacroParams macroParams,
      Attributes parameters,
      RendererState context) {
    var renderers = context.extensionRenderers();
    if (renderers.isEmpty()) {
      return null;
    }
    var extension = new ExtensionContext(
        extensionType, extensionKey, text,
        macroParams == null ? null : macroParams.values(), parameters);
    for (var renderer : renderers) {
      var rendered = CallbackGuards.guard("ExtensionRenderer", () -> renderer.render(extension), null);
      if (rendered != null) {
        return rendered;
      }
    }
    return null;
  }

  private String extensionFallback(
      @Nullable String text, @Nullable String extensionType, @Nullable String extensionKey, RendererState context) {
    if (text != null && !text.isBlank()) {
      // Attribute-derived text; a block extension emits it at column 0, so neutralise leading markers.
      return MarkdownText.escapeInlineText(text, true, context.escapeParentheses());
    }
    context.recordUnsupportedExtension(extensionType, extensionKey);
    return renderExtensionPlaceholder(extensionType, extensionKey, context);
  }

  String renderSyncBlock(SyncBlock node, RendererState context) {
    return syncBlockLabel(node.resourceId(), context);
  }

  List<String> renderBodiedSyncBlock(
      BodiedSyncBlock node, RendererState context, BlockRecursion recursion) {
    var blocks = new ArrayList<String>();
    blocks.add(syncBlockLabel(node.resourceId(), context));
    blocks.addAll(recursion.renderBlocks(node.content(), context));
    return blocks;
  }

  private String syncBlockLabel(@Nullable String resourceId, RendererState context) {
    return MarkdownText.labelToken(
        resourceId == null || resourceId.isBlank() ? "Sync block" : "Sync block: " + resourceId,
        context.escapeParentheses());
  }

  // Expand via the resolver, else the macro's placeholder token (recorded as unresolved).
  private String renderPageTreeMacro(PageTreeReference reference, RendererState context) {
    var expanded = expandPageTree(reference, context);
    if (expanded != null) {
      return expanded;
    }
    return switch (reference.macro()) {
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
  }

  // The resolver's entries as an indented bullet list ("" for an authoritative empty answer), or null
  // to fall back to the token (no resolver, a null return, or a throw — recorded as unresolved).
  private @Nullable String expandPageTree(PageTreeReference reference, RendererState context) {
    var resolver = context.pageTreeResolver();
    var entries = resolver == null
        ? null
        : CallbackGuards.guard("PageTreeResolver", () -> resolver.resolve(reference), null);
    if (entries == null) {
      context.unresolvedTracker().recordPageTree(reference);
      return null;
    }

    // Shift the shallowest entry to column 0 so a deeper-rooted list does not over-indent into code.
    var baseDepth = entries.stream().mapToInt(PageTreeEntry::depth).min().orElse(0);
    var lines = new ArrayList<String>();
    for (var entry : entries) {
      var label = pageTreeLabel(entry, context);
      if (label != null) {
        lines.add(RenderBuffer.LIST_INDENT.repeat(entry.depth() - baseDepth) + "- " + label);
      }
    }
    return String.join("\n", lines);
  }

  // A link when the page id resolves, else the escaped (single-line) title; null when nothing renders.
  private @Nullable String pageTreeLabel(PageTreeEntry entry, RendererState context) {
    var title = entry.title();
    var label = title == null ? "" : title.replaceAll("\\s+", " ").strip();
    var href = TextMarkRenderer.resolvePageId(entry.pageNodeId(), context.context());
    if (href != null) {
      return MarkdownText.link(label.isEmpty() ? href : label, href, context.escapeParentheses());
    }
    return label.isEmpty() ? null : MarkdownText.escapeInlineText(label, false, context.escapeParentheses());
  }

  // The {{pagetree:<root>}} token root: the request root with whitespace collapsed and braces dropped.
  private @Nullable String pageTreeRoot(@Nullable String root) {
    if (root == null) {
      return null;
    }
    var flattened = root.replaceAll("\\s+", " ").replace("{", "").replace("}", "").strip();
    return flattened.isEmpty() ? null : flattened;
  }

  // Expand via the resolver, else the labelled placeholder (recorded as unresolved); a null
  // reference (no source page) defers to the generic extension fallback.
  private @Nullable String renderExcerptInclude(@Nullable ExcerptIncludeReference reference, RendererState context) {
    if (reference == null) {
      return null;
    }
    var resolver = context.excerptResolver();
    var resolved = resolver == null
        ? null
        : CallbackGuards.guard("ExcerptResolver", () -> resolver.resolve(reference), null);
    if (resolved != null) {
      return resolved;
    }
    context.unresolvedTracker().recordExcerpt(reference);
    var label = reference.excerptName() == null
        ? "Excerpt include: " + reference.page()
        : "Excerpt include: " + reference.page() + " / " + reference.excerptName();
    return MarkdownText.labelToken(label, context.escapeParentheses());
  }

  // The supplied attachment inventory as a bullet list of links ("" for an authoritative empty
  // inventory), or null when no inventory was supplied — only that keeps the placeholder.
  private @Nullable String renderAttachmentsMacro(RendererState context) {
    var confluenceContext = context.confluenceContext();
    if (confluenceContext == null || !confluenceContext.attachmentsSupplied()) {
      return null;
    }
    var lines = new ArrayList<String>();
    for (var reference : confluenceContext.attachmentReferencesByTitle().values()) {
      lines.add("- " + MarkdownText.link(
          reference.title(), resolveAttachment(reference, context), context.escapeParentheses()));
    }
    return String.join("\n", lines);
  }

  private String renderTocMacro(MacroParams macroParams, RendererState context) {
    var headings = context.headings();
    if (headings.isEmpty()) {
      return "";
    }

    var range = TocLevelRange.of(macroParams);
    var filtered = headings.stream()
        .filter(heading -> range.includes(heading.level()))
        .toList();
    if (filtered.isEmpty()) {
      return "";
    }

    var baseLevel = filtered.stream().mapToInt(HeadingReference::level).min().orElse(1);
    var lines = new ArrayList<String>();
    for (var heading : filtered) {
      var indent = RenderBuffer.LIST_INDENT.repeat(Math.max(0, heading.level() - baseLevel));
      if (heading.anchor() != null && !heading.anchor().isBlank()) {
        lines.add(indent + "- "
            + MarkdownText.link(heading.text(), "#" + heading.anchor(), context.escapeParentheses()));
      } else {
        lines.add(indent + "- "
            + MarkdownText.escapeInlineText(heading.text(), false, context.escapeParentheses()));
      }
    }
    return String.join("\n", lines);
  }

  private String renderIframeMacro(MacroParams macroParams, RendererState context) {
    var src = macroParams.value("src");
    if (src == null || src.isBlank()) {
      return MarkdownText.labelToken("Embedded content", context.escapeParentheses());
    }
    return MarkdownText.link("Embedded content", src, context.escapeParentheses());
  }

  private String renderViewPdfMacro(MacroParams macroParams, RendererState context) {
    var name = macroParams.value("name");
    var attachmentReference = AttachmentReferences.resolve(macroParams, context.confluenceContext());
    if (attachmentReference == null
        || attachmentReference.fileId() == null
        || attachmentReference.fileId().isBlank()) {
      return MarkdownText.labelToken(
          name == null || name.isBlank() ? "PDF" : "PDF: " + name, context.escapeParentheses());
    }

    var label = (name == null || name.isBlank()) ? "PDF" : "PDF: " + name;
    var destination = resolveAttachment(attachmentReference, context);
    return MarkdownText.link(label, destination, context.escapeParentheses());
  }

  // The caller-resolved link for an attachment, or the synthetic attachment:<fileId> placeholder when
  // there is no AttachmentResolver or it declines.
  private String resolveAttachment(AttachmentReference reference, RendererState context) {
    var resolver = context.attachmentResolver();
    var resolved = resolver == null
        ? null
        : CallbackGuards.guardNonBlank("AttachmentResolver", () -> resolver.resolve(reference));
    return resolved != null ? resolved : "attachment:" + reference.fileId();
  }

  // The bodyless legacy chart macro: nothing recoverable in the document, so a labelled placeholder.
  private String renderChartMacro(MacroParams macroParams, RendererState context) {
    var title = macroParams.value("title");
    return MarkdownText.labelToken(
        title == null || title.isBlank() ? "Chart" : "Chart: " + title, context.escapeParentheses());
  }

  // The italic "Chart: <title>" caption; one line so the emphasis wrapping survives any title.
  private String chartCaption(MacroParams macroParams, Attributes parameters, RendererState context) {
    var title = ConfluenceSupport.chartTitle(macroParams, parameters);
    var label = title == null ? "Chart" : "Chart: " + MarkdownText.collapseLineBreaks(title).strip();
    return "*" + MarkdownText.escapeInlineText(label, false, context.escapeParentheses()) + "*";
  }

  private String renderExtensionPlaceholder(
      @Nullable String extensionType, @Nullable String extensionKey, RendererState context) {
    var label = MacroDiagnostics.label(extensionType, extensionKey);
    if (placeholderWarned.add(label)) {
      log.warn(
          "Rendering placeholder for unsupported extension: {} (logged once per converter; "
              + "further occurrences log at DEBUG, per-document detail is on diagnostics)",
          label);
    } else {
      log.debug("Rendering placeholder for unsupported extension: {}", label);
    }
    return MarkdownText.labelToken(
        "Extension".equals(label) ? "Extension" : "Extension: " + label,
        context.escapeParentheses());
  }
}
