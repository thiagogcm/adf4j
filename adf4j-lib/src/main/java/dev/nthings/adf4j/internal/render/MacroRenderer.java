package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.nthings.adf4j.HeadingReference;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.internal.AttachmentReferences;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.confluence.ExcerptKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MacroRenderer {

  private static final Logger log = LoggerFactory.getLogger(MacroRenderer.class);

  String renderExtension(Extension node, RendererState context, AdfRenderer adfRenderer) {
    return renderExtensionCore(
        node.extensionType(),
        node.extensionKey(),
        node.macroParams(),
        context,
        adfRenderer,
        false);
  }

  String renderInlineExtension(
      InlineExtension node, RendererState context, AdfRenderer adfRenderer) {
    return renderExtensionCore(
        node.extensionType(),
        node.extensionKey(),
        node.macroParams(),
        context,
        adfRenderer,
        true);
  }

  private String renderExtensionCore(
      String extensionType,
      String extensionKey,
      MacroParams macroParams,
      RendererState context,
      AdfRenderer adfRenderer,
      boolean inline) {
    if (!ConfluenceSupport.isConfluenceMacroExtension(extensionType)) {
      return renderExtensionPlaceholder(extensionType, extensionKey);
    }

    var rendered = switch (extensionKey != null ? extensionKey : "") {
      case "children" -> renderChildrenPlaceholder(macroParams);
      case "toc" -> renderTocMacro(macroParams, context);
      case "anchor" -> "";
      case "iframe" -> renderIframeMacro(macroParams);
      case "viewpdf" -> renderViewPdfMacro(macroParams, context);
      case "excerpt-include" -> renderExcerptIncludeMacro(macroParams, context, adfRenderer, inline);
      case "chart:default" -> renderChartMacro(macroParams);
      default -> null;
    };
    return rendered != null ? rendered : renderExtensionPlaceholder(extensionType, extensionKey);
  }

  List<String> renderBodiedExtension(
      BodiedExtension node, RendererState context, AdfRenderer adfRenderer) {
    if (ConfluenceSupport.isConfluenceMacroExtension(node.extensionType())
        && "excerpt".equals(node.extensionKey())) {
      return adfRenderer.renderBlocks(node.content(), context);
    }

    var blocks = new ArrayList<String>();
    blocks.add(renderExtensionPlaceholder(node.extensionType(), node.extensionKey()));
    blocks.addAll(adfRenderer.renderBlocks(node.content(), context));
    return blocks;
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

  InlineExtension extractStandaloneExcerptInclude(List<AdfInline> content) {
    if (content == null || content.isEmpty()) {
      return null;
    }

    InlineExtension candidate = null;
    for (var node : content) {
      if (node instanceof Text text) {
        if (!text.text().isBlank()) {
          return null;
        }
        continue;
      }

      if (!(node instanceof InlineExtension extension)) {
        return null;
      }

      if (!ConfluenceSupport.isConfluenceMacroExtension(extension.extensionType())
          || !"excerpt-include".equals(extension.extensionKey())) {
        return null;
      }

      if (candidate != null) {
        return null;
      }
      candidate = extension;
    }
    return candidate;
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
    var headings = context.macroContext() == null ? List.<HeadingReference>of() : context.macroContext().headings();
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
      if (heading.anchor() != null && !heading.anchor().isBlank()) {
        lines.add(indent + "- [" + heading.text() + "](#" + heading.anchor() + ")");
      } else {
        lines.add(indent + "- " + heading.text());
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
    var attachmentReference = AttachmentReferences.resolve(macroParams, context.attachmentReferencesByTitle());
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

  private String renderExcerptIncludeMacro(
      MacroParams macroParams, RendererState context, AdfRenderer adfRenderer, boolean inline) {
    var pageTitle = Stream.of(macroParams.value(""), context.pageTitle())
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    var excerptName = Stream.of(macroParams.value("name"))
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    var placeholder = "[Excerpt include: %s]".formatted(label(pageTitle, excerptName));

    if (context.macroContext() == null || pageTitle == null || pageTitle.isBlank()) {
      return placeholder;
    }

    var key = new ExcerptKey(pageTitle, excerptName);
    if (context.isExcerptActive(key)) {
      return placeholder;
    }

    var excerptBlocks = context.macroContext().excerpts().get(key);
    if (excerptBlocks == null || excerptBlocks.isEmpty()) {
      return placeholder;
    }

    var rendered = adfRenderer.joinBlocks(adfRenderer.renderBlocks(excerptBlocks, context.withExcerpt(key)));
    if (!inline || context.inTable()) {
      return rendered;
    }
    return rendered.replace("\n", "<br>");
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

  private String label(String pageTitle, String excerptName) {
    return String.join(
        " / ", Stream.of(pageTitle, excerptName).filter(s -> s != null && !s.isBlank()).toList());
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
