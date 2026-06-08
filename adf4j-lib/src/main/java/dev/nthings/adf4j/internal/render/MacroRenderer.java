package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import dev.nthings.adf4j.extension.ExtensionContext;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.ast.AdfBlock;
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
import dev.nthings.adf4j.options.PageTreeEntry;
import dev.nthings.adf4j.options.PageTreeMacro;
import dev.nthings.adf4j.options.PageTreeRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MacroRenderer {

  private static final Logger log = LoggerFactory.getLogger(MacroRenderer.class);

  String renderExtension(Extension node, RendererState context) {
    return renderExtensionCore(
        node.extensionType(), node.extensionKey(), node.text(), node.macroParams(), context);
  }

  String renderInlineExtension(InlineExtension node, RendererState context) {
    return renderExtensionCore(
        node.extensionType(), node.extensionKey(), node.text(), node.macroParams(), context);
  }

  private String renderExtensionCore(
      String extensionType,
      String extensionKey,
      String text,
      MacroParams macroParams,
      RendererState context) {
    var custom = renderCustom(extensionType, extensionKey, text, macroParams, context);
    if (custom.isPresent()) {
      return custom.get();
    }
    if (!ConfluenceSupport.isConfluenceMacroExtension(extensionType)) {
      return extensionFallback(text, extensionType, extensionKey, context);
    }

    var rendered = switch (extensionKey != null ? extensionKey : "") {
      case "children" -> renderChildren(macroParams, context);
      case "pagetree" -> renderPageTree(macroParams, context);
      case "toc" -> renderTocMacro(macroParams, context);
      case "anchor" -> HtmlFragments.anchor(ConfluenceSupport.anchorId(macroParams));
      case "iframe" -> renderIframeMacro(macroParams, context);
      case "viewpdf" -> renderViewPdfMacro(macroParams, context);
      case "chart:default" -> renderChartMacro(macroParams, context);
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
    return headerThenBodies(
        node.text(), node.extensionType(), node.extensionKey(), node.macroParams(),
        node.content(), context, recursion);
  }

  List<String> renderMultiBodiedExtension(
      MultiBodiedExtension node, RendererState context, BlockRecursion recursion) {
    // The schema predates this node; salvage the frame bodies.
    return headerThenBodies(
        node.text(), node.extensionType(), node.extensionKey(), node.macroParams(),
        node.content(), context, recursion);
  }

  // Header (custom renderer, else macro text or "[Extension: …]" placeholder) then the body blocks.
  private List<String> headerThenBodies(
      String text,
      String extensionType,
      String extensionKey,
      MacroParams macroParams,
      List<AdfBlock> content,
      RendererState context,
      BlockRecursion recursion) {
    var blocks = new ArrayList<String>();
    blocks.add(
        renderCustom(extensionType, extensionKey, text, macroParams, context)
            .orElseGet(() -> extensionFallback(text, extensionType, extensionKey, context)));
    blocks.addAll(recursion.renderBlocks(content, context));
    return blocks;
  }

  // Custom renderers, consulted in order (first non-empty wins); empty defers to the next, then default.
  private Optional<String> renderCustom(
      String extensionType,
      String extensionKey,
      String text,
      MacroParams macroParams,
      RendererState context) {
    var renderers = context.extensionRenderers();
    if (renderers.isEmpty()) {
      return Optional.empty();
    }
    var extension = new ExtensionContext(
        extensionType, extensionKey, text, macroParams == null ? null : macroParams.values());
    for (var renderer : renderers) {
      var rendered = CallbackGuards.guard(
          "ExtensionRenderer", () -> renderer.render(extension), Optional.<String>empty());
      if (rendered != null && rendered.isPresent()) {
        return rendered;
      }
    }
    return Optional.empty();
  }

  private String extensionFallback(
      String text, String extensionType, String extensionKey, RendererState context) {
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

  private String syncBlockLabel(String resourceId, RendererState context) {
    return MarkdownText.labelToken(
        resourceId == null || resourceId.isBlank() ? "Sync block" : "Sync block: " + resourceId,
        context.escapeParentheses());
  }

  // Expand the child pages via the resolver, else the {{children}} / {{children:<depth>}} token.
  private String renderChildren(MacroParams macroParams, RendererState context) {
    var request =
        new PageTreeRequest(PageTreeMacro.CHILDREN, rootParam(macroParams, "page"), macroParams.values());
    var expanded = expandPageTree(request, context);
    return expanded != null ? expanded : childrenToken(macroParams);
  }

  private String childrenToken(MacroParams macroParams) {
    var all = allChildrenValue(macroParams);
    if (all != null && "true".equalsIgnoreCase(all)) {
      return "{{children}}";
    }

    var depth = macroParams.value("depth");
    if (depth != null) {
      try {
        var parsed = Integer.parseInt(depth);
        if (parsed > 0) {
          return "{{children:" + parsed + "}}";
        }
      } catch (NumberFormatException _) {
        // fall through to default
      }
    }
    return "{{children}}";
  }

  // Expand the page tree via the resolver, else the {{pagetree}} / {{pagetree:<root>}} token.
  private String renderPageTree(MacroParams macroParams, RendererState context) {
    var request =
        new PageTreeRequest(PageTreeMacro.PAGETREE, rootParam(macroParams, "root"), macroParams.values());
    var expanded = expandPageTree(request, context);
    if (expanded != null) {
      return expanded;
    }
    var root = pageTreeRoot(macroParams);
    return root == null ? "{{pagetree}}" : "{{pagetree:" + root + "}}";
  }

  // The resolver's descendant pages as an indented bullet list, or null to fall back to the token (no
  // resolver, it declines/throws, or nothing renderable).
  private String expandPageTree(PageTreeRequest request, RendererState context) {
    var resolver = context.pageTreeResolver();
    if (resolver == null) {
      return null;
    }
    var entries = CallbackGuards.guard("PageTreeResolver", () -> resolver.resolve(request), null);
    if (entries == null || entries.isEmpty()) {
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
    return lines.isEmpty() ? null : String.join("\n", lines);
  }

  // A link when the page id resolves, else the escaped (single-line) title; null when nothing renders.
  private String pageTreeLabel(PageTreeEntry entry, RendererState context) {
    var title = entry.title();
    var label = title == null ? "" : title.replaceAll("\\s+", " ").strip();
    var href = resolvePage(entry.pageNodeId(), context);
    if (href != null) {
      return MarkdownText.link(label.isEmpty() ? href : label, href, context.escapeParentheses());
    }
    return label.isEmpty() ? null : MarkdownText.escapeInlineText(label, false, context.escapeParentheses());
  }

  // The page id routed through the caller's PageLinkResolver (the hook used for inline page links), or
  // null when there is no id/resolver or it declines.
  private String resolvePage(String pageNodeId, RendererState context) {
    var resolver = context.pageLinkResolver();
    if (pageNodeId == null || pageNodeId.isBlank() || resolver == null) {
      return null;
    }
    var resolved = CallbackGuards.guard("PageLinkResolver", () -> resolver.resolve(pageNodeId), null);
    return resolved == null || resolved.isBlank() ? null : resolved;
  }

  // A macro root parameter (trimmed), or null for a blank or "@keyword" root.
  private String rootParam(MacroParams macroParams, String name) {
    var value = macroParams.value(name);
    if (value == null) {
      return null;
    }
    var trimmed = value.strip();
    return trimmed.isEmpty() || trimmed.startsWith("@") ? null : trimmed;
  }

  // The {{pagetree:<root>}} token root: the "root" param with whitespace collapsed and braces dropped.
  private String pageTreeRoot(MacroParams macroParams) {
    var root = rootParam(macroParams, "root");
    if (root == null) {
      return null;
    }
    var flattened = root.replaceAll("\\s+", " ").replace("{", "").replace("}", "").strip();
    return flattened.isEmpty() ? null : flattened;
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

  private String allChildrenValue(MacroParams macroParams) {
    return Stream.of(macroParams.value("all"), macroParams.value("allChildren"))
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
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
    var attachmentReference =
        AttachmentReferences.resolve(macroParams, context.attachmentReferencesByTitle());
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
  // there is no AttachmentResolver or it declines (null/blank).
  private String resolveAttachment(AttachmentReference reference, RendererState context) {
    var resolver = context.attachmentResolver();
    if (resolver != null) {
      var resolved = CallbackGuards.guard("AttachmentResolver", () -> resolver.resolve(reference), null);
      if (resolved != null && !resolved.isBlank()) {
        return resolved;
      }
    }
    return "attachment:" + reference.fileId();
  }

  private String renderChartMacro(MacroParams macroParams, RendererState context) {
    var title = macroParams.value("title");
    return MarkdownText.labelToken(
        title == null || title.isBlank() ? "Chart" : "Chart: " + title, context.escapeParentheses());
  }

  private String renderExtensionPlaceholder(
      String extensionType, String extensionKey, RendererState context) {
    if (extensionType != null && extensionKey != null) {
      log.warn("Rendering placeholder for unsupported extension: {}/{}", extensionType, extensionKey);
      return MarkdownText.labelToken(
          "Extension: " + extensionType + "/" + extensionKey, context.escapeParentheses());
    }
    if (extensionKey != null) {
      log.warn("Rendering placeholder for unsupported extension key: {}", extensionKey);
      return MarkdownText.labelToken("Extension: " + extensionKey, context.escapeParentheses());
    }
    log.warn("Rendering placeholder for extension with no type/key");
    return MarkdownText.labelToken("Extension", context.escapeParentheses());
  }
}
