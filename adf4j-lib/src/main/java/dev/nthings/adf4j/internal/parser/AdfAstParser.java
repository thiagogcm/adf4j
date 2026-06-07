package dev.nthings.adf4j.internal.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.Alignment;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.Annotation;
import dev.nthings.adf4j.ast.BackgroundColor;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.Border;
import dev.nthings.adf4j.ast.Breakout;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.CardAttrs;
import dev.nthings.adf4j.ast.Code;
import dev.nthings.adf4j.ast.CodeBlock;
import dev.nthings.adf4j.ast.DataConsumer;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.Em;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.FontSize;
import dev.nthings.adf4j.ast.Fragment;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.Indentation;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.LayoutColumn;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.Link;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaAttrs;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.NestedExpand;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Panel;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Placeholder;
import dev.nthings.adf4j.ast.Rule;
import dev.nthings.adf4j.ast.Status;
import dev.nthings.adf4j.ast.Strike;
import dev.nthings.adf4j.ast.Strong;
import dev.nthings.adf4j.ast.SubSup;
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.TextColor;
import dev.nthings.adf4j.ast.Underline;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.ast.UnknownMark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class AdfAstParser {

  private static final Logger log = LoggerFactory.getLogger(AdfAstParser.class);

  private final JsonMapper mapper;

  public AdfAstParser(JsonMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  public AdfDocument parseDocument(JsonNode root) {
    if (root == null || !root.isObject()) {
      return new AdfDocument(1, List.of());
    }
    return new AdfDocument(JsonFields.integer(root, "version", 1), parseBlocks(root.get("content")));
  }

  // Maps every object element of a JSON array through fn, skipping non-objects and any null fn maps to.
  // The single place array traversal lives; type-filtered lists differ only by what their fn returns.
  private <T> List<T> mapArray(JsonNode arrayNode, Function<JsonNode, T> fn) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<T>(arrayNode.size());
    for (var child : arrayNode) {
      if (child == null || !child.isObject()) {
        continue;
      }
      var mapped = fn.apply(child);
      if (mapped != null) {
        result.add(mapped);
      }
    }
    return result;
  }

  // True when node's "type" field equals any of the given values.
  private static boolean isType(JsonNode node, String... types) {
    var type = JsonFields.text(node, "type", "");
    for (var candidate : types) {
      if (candidate.equals(type)) {
        return true;
      }
    }
    return false;
  }

  List<AdfBlock> parseBlocks(JsonNode arrayNode) {
    return mapArray(arrayNode, this::parseBlock);
  }

  AdfBlock parseBlock(JsonNode node) {
    var type = JsonFields.text(node, "type", "");
    return switch (type) {
      case "paragraph" -> new Paragraph(parseInlines(node.get("content")), parseMarks(node.get("marks")));
      case "heading" -> parseHeading(node);
      case "blockquote" -> new Blockquote(parseBlocks(node.get("content")));
      case "codeBlock" -> parseCodeBlock(node);
      case "panel" -> new Panel(JsonFields.text(node.path("attrs"), "panelType"), parseBlocks(node.get("content")));
      case "rule" -> new Rule();
      case "bulletList" -> new BulletList(parseListItems(node.get("content")));
      case "orderedList" -> parseOrderedList(node);
      case "listItem" -> new ListItem(parseBlocks(node.get("content")));
      case "taskList" -> new TaskList(parseTaskListItems(node.get("content")));
      case "taskItem" -> new TaskItem(
          JsonFields.text(node.path("attrs"), "state"), parseInlines(node.get("content")));
      case "blockTaskItem" -> new BlockTaskItem(
          JsonFields.text(node.path("attrs"), "state"), parseBlocks(node.get("content")));
      case "decisionList" -> new DecisionList(parseDecisionItems(node.get("content")));
      case "decisionItem" -> new DecisionItem(
          JsonFields.text(node.path("attrs"), "state"), parseInlines(node.get("content")));
      case "table" -> parseTable(node);
      case "tableRow" -> new TableRow(parseTableCells(node.get("content")));
      case "tableCell" -> parseTableCell(node, false);
      case "tableHeader" -> parseTableCell(node, true);
      case "mediaSingle" -> parseMediaSingle(node);
      case "mediaGroup" -> new MediaGroup(parseBlocks(node.get("content")));
      case "media" -> new Media(parseMediaAttrs(node.path("attrs")), parseMarks(node.get("marks")));
      case "caption" -> new Caption(parseInlines(node.get("content")));
      case "expand" -> new Expand(JsonFields.text(node.path("attrs"), "title", ""), parseBlocks(node.get("content")));
      case "nestedExpand" -> new NestedExpand(
          JsonFields.text(node.path("attrs"), "title", ""), parseBlocks(node.get("content")));
      case "layoutSection" -> new LayoutSection(parseLayoutColumns(node.get("content")));
      case "layoutColumn" -> new LayoutColumn(
          JsonFields.integer(node.path("attrs"), "width", 0), parseBlocks(node.get("content")));
      case "extension" -> parseExtension(node);
      case "bodiedExtension" -> parseBodiedExtension(node);
      case "multiBodiedExtension" -> parseMultiBodiedExtension(node);
      case "extensionFrame" -> new ExtensionFrame(parseBlocks(node.get("content")));
      case "syncBlock" -> new SyncBlock(JsonFields.text(node.path("attrs"), "resourceId"));
      case "bodiedSyncBlock" -> new BodiedSyncBlock(
          JsonFields.text(node.path("attrs"), "resourceId"), parseBlocks(node.get("content")));
      case "blockCard" -> new BlockCard(parseCardAttrs(node.path("attrs")));
      case "embedCard" -> new EmbedCard(parseCardAttrs(node.path("attrs")));
      default -> new UnknownBlock(type, rawJson(node));
    };
  }

  public List<AdfInline> parseInlines(JsonNode arrayNode) {
    return mapArray(arrayNode, this::parseInline);
  }

  AdfInline parseInline(JsonNode node) {
    var type = JsonFields.text(node, "type", "");
    return switch (type) {
      case "text" -> new Text(JsonFields.text(node, "text", ""), parseMarks(node.get("marks")));
      case "hardBreak" -> new HardBreak();
      case "inlineCard" -> new InlineCard(parseCardAttrs(node.path("attrs")));
      case "embedCard" -> new InlineCard(parseCardAttrs(node.path("attrs")));
      case "blockCard" -> new InlineCard(parseCardAttrs(node.path("attrs")));
      case "mediaInline" -> new MediaInline(parseMediaAttrs(node.path("attrs")), parseMarks(node.get("marks")));
      case "date" -> new Date(
          JsonFields.text(node.path("attrs"), "timestamp"),
          JsonFields.text(node.path("attrs"), "localId"));
      case "emoji" -> new Emoji(
          JsonFields.text(node.path("attrs"), "id"),
          JsonFields.text(node.path("attrs"), "text"),
          JsonFields.text(node.path("attrs"), "shortName"));
      case "mention" -> new Mention(
          JsonFields.text(node.path("attrs"), "id"),
          mentionText(node.path("attrs")),
          JsonFields.text(node.path("attrs"), "userType"),
          JsonFields.text(node.path("attrs"), "accessLevel"),
          JsonFields.text(node.path("attrs"), "localId"));
      case "placeholder" -> new Placeholder(JsonFields.text(node.path("attrs"), "text", ""));
      case "status" -> new Status(
          JsonFields.text(node.path("attrs"), "text"),
          JsonFields.text(node.path("attrs"), "color"),
          JsonFields.text(node.path("attrs"), "style"),
          JsonFields.text(node.path("attrs"), "localId"));
      case "inlineExtension" -> parseInlineExtension(node);
      default -> new UnknownInline(type, rawJson(node));
    };
  }

  List<AdfMark> parseMarks(JsonNode arrayNode) {
    return mapArray(arrayNode, this::parseMark);
  }

  AdfMark parseMark(JsonNode node) {
    var type = JsonFields.text(node, "type", "");
    var attrs = node.path("attrs");
    return switch (type) {
      case "strong" -> new Strong();
      case "em" -> new Em();
      case "code" -> new Code();
      case "strike" -> new Strike();
      case "underline" -> new Underline();
      case "subsup" -> new SubSup(JsonFields.text(attrs, "type"));
      case "link" -> new Link(
          JsonFields.text(attrs, "href"),
          JsonFields.text(attrs, "title"),
          toAttributes(attrs));
      case "textColor" -> new TextColor(JsonFields.text(attrs, "color"));
      case "backgroundColor" -> new BackgroundColor(JsonFields.text(attrs, "color"));
      case "alignment" -> new Alignment(JsonFields.text(attrs, "align"));
      case "indentation" -> new Indentation(JsonFields.integer(attrs, "level", 0));
      case "fontSize" -> new FontSize(JsonFields.text(attrs, "fontSize"));
      case "border" -> new Border(JsonFields.text(attrs, "color"), JsonFields.text(attrs, "size"));
      case "annotation" -> new Annotation();
      case "breakout" -> new Breakout();
      case "fragment" -> new Fragment();
      case "dataConsumer" -> new DataConsumer();
      default -> new UnknownMark(type, rawJson(node));
    };
  }

  public MacroParams parseMacroParams(JsonNode macroParams) {
    if (macroParams == null || !macroParams.isObject()) {
      return MacroParams.empty();
    }
    var values = new LinkedHashMap<String, String>();
    for (var entry : macroParams.properties()) {
      var value = entry.getValue();
      if (value == null || value.isNull() || value.isMissingNode()) {
        continue;
      }
      String resolved = null;
      if (value.isObject()) {
        var inner = value.get("value");
        if (inner != null && !inner.isNull() && inner.isValueNode()) {
          resolved = inner.asString();
        }
      } else if (value.isValueNode()) {
        resolved = value.asString();
      }
      if (resolved != null) {
        values.put(entry.getKey(), resolved);
      }
    }
    return new MacroParams(values);
  }

  private Heading parseHeading(JsonNode node) {
    var level = Math.clamp(JsonFields.integer(node.path("attrs"), "level", 1), 1, 6);
    return new Heading(level, parseInlines(node.get("content")), parseMarks(node.get("marks")));
  }

  private CodeBlock parseCodeBlock(JsonNode node) {
    var language = JsonFields.text(node.path("attrs"), "language");
    var builder = new StringBuilder();
    var content = node.get("content");
    if (content != null && content.isArray()) {
      for (var child : content) {
        builder.append(JsonFields.text(child, "text", ""));
      }
    }
    return new CodeBlock(language, builder.toString());
  }

  private List<ListItem> parseListItems(JsonNode arrayNode) {
    return mapArray(arrayNode, child ->
        isType(child, "listItem") ? new ListItem(parseBlocks(child.get("content"))) : null);
  }

  private OrderedList parseOrderedList(JsonNode node) {
    var order = JsonFields.integer(node.path("attrs"), "order", 1);
    return new OrderedList(order, parseListItems(node.get("content")));
  }

  // A nested taskList is schema-valid; parse it as a TaskList block so the renderer can recurse.
  private List<AdfBlock> parseTaskListItems(JsonNode arrayNode) {
    return mapArray(arrayNode, child ->
        isType(child, "taskItem", "blockTaskItem", "taskList") ? parseBlock(child) : null);
  }

  private List<DecisionItem> parseDecisionItems(JsonNode arrayNode) {
    return mapArray(arrayNode, child ->
        isType(child, "decisionItem")
            ? new DecisionItem(
                JsonFields.text(child.path("attrs"), "state"), parseInlines(child.get("content")))
            : null);
  }

  private Table parseTable(JsonNode node) {
    var numberColumnEnabled = JsonFields.bool(node.path("attrs"), "isNumberColumnEnabled", false);
    var rows = mapArray(node.get("content"), rowNode ->
        isType(rowNode, "tableRow")
            ? new TableRow(parseTableCells(rowNode.get("content")))
            : null);
    return new Table(numberColumnEnabled, rows);
  }

  private List<TableCell> parseTableCells(JsonNode arrayNode) {
    return mapArray(arrayNode, cellNode ->
        isType(cellNode, "tableCell", "tableHeader")
            ? parseTableCell(cellNode, isType(cellNode, "tableHeader"))
            : null);
  }

  private TableCell parseTableCell(JsonNode node, boolean header) {
    var attrs = node.path("attrs");
    var colspan = JsonFields.integer(attrs, "colspan", 1);
    var rowspan = JsonFields.integer(attrs, "rowspan", 1);
    var background = JsonFields.text(attrs, "background");
    return new TableCell(header, colspan, rowspan, background, parseBlocks(node.get("content")));
  }

  private MediaSingle parseMediaSingle(JsonNode node) {
    var attrs = node.path("attrs");
    var layout = JsonFields.text(attrs, "layout");
    var widthType = JsonFields.text(attrs, "widthType");
    var width = JsonFields.text(attrs, "width");
    return new MediaSingle(
        layout, widthType, width, parseBlocks(node.get("content")), parseMarks(node.get("marks")));
  }

  private List<LayoutColumn> parseLayoutColumns(JsonNode arrayNode) {
    return mapArray(arrayNode, child ->
        isType(child, "layoutColumn")
            ? new LayoutColumn(
                JsonFields.integer(child.path("attrs"), "width", 0), parseBlocks(child.get("content")))
            : null);
  }

  private record ExtensionFields(String type, String key, String text, MacroParams macroParams) {
  }

  private ExtensionFields extensionFields(JsonNode attrs) {
    return new ExtensionFields(
        JsonFields.text(attrs, "extensionType"),
        JsonFields.text(attrs, "extensionKey"),
        JsonFields.text(attrs, "text"),
        parseMacroParams(attrs.path("parameters").path("macroParams")));
  }

  private Extension parseExtension(JsonNode node) {
    var fields = extensionFields(node.path("attrs"));
    return new Extension(fields.type(), fields.key(), fields.text(), fields.macroParams());
  }

  private BodiedExtension parseBodiedExtension(JsonNode node) {
    var fields = extensionFields(node.path("attrs"));
    return new BodiedExtension(
        fields.type(), fields.key(), fields.text(), fields.macroParams(),
        parseBlocks(node.get("content")));
  }

  private MultiBodiedExtension parseMultiBodiedExtension(JsonNode node) {
    var fields = extensionFields(node.path("attrs"));
    return new MultiBodiedExtension(
        fields.type(), fields.key(), fields.text(), fields.macroParams(),
        parseBlocks(node.get("content")));
  }

  private InlineExtension parseInlineExtension(JsonNode node) {
    var fields = extensionFields(node.path("attrs"));
    return new InlineExtension(fields.type(), fields.key(), fields.text(), fields.macroParams());
  }

  private CardAttrs parseCardAttrs(JsonNode attrs) {
    if (attrs == null || !attrs.isObject()) {
      return new CardAttrs(null, null, null, null, Attributes.empty());
    }
    var data = attrs.path("data");
    var url = JsonFields.text(attrs, "url");
    if (url == null || url.isBlank()) {
      url = JsonFields.text(data, "url");
    }
    var datasourceId = JsonFields.text(attrs.path("datasource"), "id");
    var localId = JsonFields.text(attrs, "localId");
    var title = JsonFields.text(data, "name");
    return new CardAttrs(url, datasourceId, localId, title, toAttributes(attrs));
  }

  private MediaAttrs parseMediaAttrs(JsonNode attrs) {
    if (attrs == null || !attrs.isObject()) {
      return MediaAttrs.builder().build();
    }
    return MediaAttrs.builder()
        .type(JsonFields.text(attrs, "type"))
        .id(JsonFields.text(attrs, "id"))
        .localId(JsonFields.text(attrs, "localId"))
        .url(JsonFields.text(attrs, "url"))
        .collection(JsonFields.text(attrs, "collection"))
        .alt(JsonFields.text(attrs, "alt"))
        .width(JsonFields.text(attrs, "width"))
        .height(JsonFields.text(attrs, "height"))
        .mediaType(JsonFields.text(attrs, "mediaType"))
        .fileMimeType(JsonFields.text(attrs, "__fileMimeType"))
        .fileName(JsonFields.text(attrs, "__fileName"))
        .name(JsonFields.text(attrs, "name"))
        .build();
  }

  /**
   * Converts a node's raw {@code attrs} into a generic {@link Attributes} view of plain Java values.
   * The conversion is product-neutral: every key (including any {@code __*} extension keys) is copied
   * as-is, leaving interpretation of product-specific extras to higher layers.
   */
  private Attributes toAttributes(JsonNode attrs) {
    if (attrs == null || !attrs.isObject()) {
      return Attributes.empty();
    }
    var values = new LinkedHashMap<String, Object>();
    for (var entry : attrs.properties()) {
      var value = toPlainValue(entry.getValue());
      if (value != null) {
        values.put(entry.getKey(), value);
      }
    }
    return new Attributes(values);
  }

  private Object toPlainValue(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    if (node.isObject()) {
      var map = new LinkedHashMap<String, Object>();
      for (var entry : node.properties()) {
        var value = toPlainValue(entry.getValue());
        if (value != null) {
          map.put(entry.getKey(), value);
        }
      }
      return Map.copyOf(map);
    }
    if (node.isArray()) {
      // Keep null elements so array indices are preserved (List.copyOf would reject the nulls).
      var items = new ArrayList<>(node.size());
      for (var child : node) {
        items.add(toPlainValue(child));
      }
      return Collections.unmodifiableList(items);
    }
    if (node.isBoolean()) {
      return node.asBoolean();
    }
    if (node.isIntegralNumber()) {
      return node.asLong();
    }
    if (node.isNumber()) {
      return node.asDouble();
    }
    return node.asString();
  }

  private String mentionText(JsonNode attrs) {
    var raw = JsonFields.text(attrs, "text");
    return raw == null ? "" : raw.strip();
  }

  private String rawJson(JsonNode node) {
    try {
      return mapper.writeValueAsString(node);
    } catch (JacksonException exception) {
      log.warn("Failed to re-serialise unsupported node to raw JSON: {}", exception.getMessage());
      return "{}";
    }
  }
}
