package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;

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
    if (!ConfluenceSupport.isConfluenceMacroExtension(extensionType)) {
      return extensionFallback(text, extensionType, extensionKey);
    }

    var rendered = switch (extensionKey != null ? extensionKey : "") {
      case "children" -> renderChildrenPlaceholder(macroParams);
      case "toc" -> renderTocMacro(macroParams, context);
      case "anchor" -> "";
      case "iframe" -> renderIframeMacro(macroParams);
      case "viewpdf" -> renderViewPdfMacro(macroParams, context);
      case "chart:default" -> renderChartMacro(macroParams);
      default -> null;
    };
    return rendered != null ? rendered : extensionFallback(text, extensionType, extensionKey);
  }

  List<String> renderBodiedExtension(
      BodiedExtension node, RendererState context, AdfRenderer adfRenderer) {
    if (ConfluenceSupport.isConfluenceMacroExtension(node.extensionType())
        && "excerpt".equals(node.extensionKey())) {
      return adfRenderer.renderBlocks(node.content(), context);
    }

    var blocks = new ArrayList<String>();
    blocks.add(extensionFallback(node.text(), node.extensionType(), node.extensionKey()));
    blocks.addAll(adfRenderer.renderBlocks(node.content(), context));
    return blocks;
  }

  private String extensionFallback(String text, String extensionType, String extensionKey) {
    if (text != null && !text.isBlank()) {
      return text;
    }
    return renderExtensionPlaceholder(extensionType, extensionKey);
  }

  String renderSyncBlock(SyncBlock node) {
    var resourceId = node.resourceId();
    if (resourceId == null || resourceId.isBlank()) {
      return "[Sync block]";
    }
    return "[Sync block: %s]".formatted(resourceId);
  }

  List<String> renderBodiedSyncBlock(
      BodiedSyncBlock node, RendererState context, AdfRenderer adfRenderer) {
    var blocks = new ArrayList<String>();
    var resourceId = node.resourceId();
    blocks.add(
        resourceId == null || resourceId.isBlank()
            ? "[Sync block]"
            : "[Sync block: %s]".formatted(resourceId));
    blocks.addAll(adfRenderer.renderBlocks(node.content(), context));
    return blocks;
  }

  private String renderChildrenPlaceholder(MacroParams macroParams) {
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

  private String renderTocMacro(MacroParams macroParams, RendererState context) {
    var headings = context.headings();
    if (headings.isEmpty()) {
      return "";
    }

    var rawMin = parseIntOrDefault(macroParams.value("minLevel"), 1);
    var rawMax = parseIntOrDefault(macroParams.value("maxLevel"), 6);
    var minLevel = Math.clamp(Math.min(rawMin, rawMax), 1, 6);
    var maxLevel = Math.clamp(Math.max(rawMin, rawMax), 1, 6);

    var filtered = headings.stream()
        .filter(heading -> heading.level() >= minLevel && heading.level() <= maxLevel)
        .toList();
    if (filtered.isEmpty()) {
      return "";
    }

    var baseLevel = filtered.stream().mapToInt(HeadingReference::level).min().orElse(1);
    var lines = new ArrayList<String>();
    for (var heading : filtered) {
      var indent = RenderBuffer.LIST_INDENT.repeat(Math.max(0, heading.level() - baseLevel));
      var label = MarkdownText.escapeLinkText(heading.text());
      if (heading.anchor() != null && !heading.anchor().isBlank()) {
        lines.add(indent + "- [" + label + "](#" + heading.anchor() + ")");
      } else {
        lines.add(indent + "- " + label);
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

  private String renderIframeMacro(MacroParams macroParams) {
    var src = macroParams.value("src");
    if (src == null || src.isBlank()) {
      return "[Embedded content]";
    }
    return "[Embedded content](%s)".formatted(src);
  }

  private String renderViewPdfMacro(MacroParams macroParams, RendererState context) {
    var name = macroParams.value("name");
    var attachmentReference =
        AttachmentReferences.resolve(macroParams, context.attachmentReferencesByTitle());
    if (attachmentReference == null
        || attachmentReference.fileId() == null
        || attachmentReference.fileId().isBlank()) {
      return name == null || name.isBlank() ? "[PDF]" : "[PDF: %s]".formatted(name);
    }

    var label = (name == null || name.isBlank()) ? "PDF" : "PDF: " + name;
    return "[%s](attachment:%s)".formatted(label, attachmentReference.fileId());
  }

  private String renderChartMacro(MacroParams macroParams) {
    var title = macroParams.value("title");
    return title == null || title.isBlank() ? "[Chart]" : "[Chart: %s]".formatted(title);
  }

  private int parseIntOrDefault(String raw, int fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException _) {
      return fallback;
    }
  }

  private String renderExtensionPlaceholder(String extensionType, String extensionKey) {
    if (extensionType != null && extensionKey != null) {
      log.debug("Rendering placeholder for unsupported extension: {}/{}", extensionType, extensionKey);
      return "[Extension: %s/%s]".formatted(extensionType, extensionKey);
    }
    if (extensionKey != null) {
      log.debug("Rendering placeholder for unsupported extension key: {}", extensionKey);
      return "[Extension: %s]".formatted(extensionKey);
    }
    log.debug("Rendering placeholder for extension with no type/key");
    return "[Extension]";
  }
}
