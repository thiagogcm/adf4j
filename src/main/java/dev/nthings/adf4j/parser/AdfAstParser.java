package dev.nthings.adf4j.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import dev.nthings.adf4j.AdfJson;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.Alignment;
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
import dev.nthings.adf4j.ast.ConfluenceMetadata;
import dev.nthings.adf4j.ast.DataConsumer;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.Em;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.Extension;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class AdfAstParser {

  private final JsonMapper mapper;

  public AdfAstParser(JsonMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  public AdfDocument parseDocument(JsonNode root) {
    if (root == null || !root.isObject()) {
      return new AdfDocument(1, List.of());
    }
    var version = root.path("version").asInt(1);
    return new AdfDocument(version, parseBlocks(root.get("content")));
  }

  public List<AdfBlock> parseBlocks(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var blocks = new ArrayList<AdfBlock>(arrayNode.size());
    for (var child : arrayNode) {
      if (child == null || !child.isObject()) {
        continue;
      }
      blocks.add(parseBlock(child));
    }
    return blocks;
  }

  public AdfBlock parseBlock(JsonNode node) {
    var type = AdfJson.text(node, "type", "");
    return switch (type) {
      case "paragraph" -> new Paragraph(parseInlines(node.get("content")), parseMarks(node.get("marks")));
      case "heading" -> parseHeading(node);
      case "blockquote" -> new Blockquote(parseBlocks(node.get("content")));
      case "codeBlock" -> parseCodeBlock(node);
      case "panel" -> new Panel(AdfJson.text(node.path("attrs"), "panelType"), parseBlocks(node.get("content")));
      case "rule" -> new Rule();
      case "bulletList" -> new BulletList(parseListItems(node.get("content")));
      case "orderedList" -> parseOrderedList(node);
      case "listItem" -> new ListItem(parseBlocks(node.get("content")));
      case "taskList" -> new TaskList(parseTaskListItems(node.get("content")));
      case "taskItem" -> new TaskItem(
          AdfJson.text(node.path("attrs"), "state"), parseInlines(node.get("content")));
      case "blockTaskItem" -> new BlockTaskItem(
          AdfJson.text(node.path("attrs"), "state"), parseBlocks(node.get("content")));
      case "decisionList" -> new DecisionList(parseDecisionItems(node.get("content")));
      case "decisionItem" -> new DecisionItem(
          AdfJson.text(node.path("attrs"), "state"), parseInlines(node.get("content")));
      case "table" -> parseTable(node);
      case "tableRow" -> new TableRow(parseTableCells(node.get("content")));
      case "tableCell" -> parseTableCell(node, false);
      case "tableHeader" -> parseTableCell(node, true);
      case "mediaSingle" -> parseMediaSingle(node);
      case "mediaGroup" -> new MediaGroup(parseMediaBlocks(node.get("content")));
      case "media" -> new Media(parseMediaAttrs(node.path("attrs")), parseMarks(node.get("marks")));
      case "caption" -> new Caption(parseInlines(node.get("content")));
      case "expand" -> new Expand(AdfJson.text(node.path("attrs"), "title", ""), parseBlocks(node.get("content")));
      case "nestedExpand" -> new NestedExpand(
          AdfJson.text(node.path("attrs"), "title", ""), parseBlocks(node.get("content")));
      case "layoutSection" -> new LayoutSection(parseLayoutColumns(node.get("content")));
      case "layoutColumn" -> new LayoutColumn(
          node.path("attrs").path("width").asInt(0), parseBlocks(node.get("content")));
      case "extension" -> parseExtension(node);
      case "bodiedExtension" -> parseBodiedExtension(node);
      case "syncBlock" -> new SyncBlock(AdfJson.text(node.path("attrs"), "resourceId"));
      case "bodiedSyncBlock" -> new BodiedSyncBlock(
          AdfJson.text(node.path("attrs"), "resourceId"), parseBlocks(node.get("content")));
      case "blockCard" -> new BlockCard(parseCardAttrs(node.path("attrs")));
      case "embedCard" -> new EmbedCard(parseCardAttrs(node.path("attrs")));
      default -> new UnknownBlock(type, rawJson(node));
    };
  }

  public List<AdfInline> parseInlines(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var inlines = new ArrayList<AdfInline>(arrayNode.size());
    for (var child : arrayNode) {
      if (child == null || !child.isObject()) {
        continue;
      }
      inlines.add(parseInline(child));
    }
    return inlines;
  }

  public AdfInline parseInline(JsonNode node) {
    var type = AdfJson.text(node, "type", "");
    return switch (type) {
      case "text" -> new Text(AdfJson.text(node, "text", ""), parseMarks(node.get("marks")));
      case "hardBreak" -> new HardBreak();
      case "inlineCard" -> new InlineCard(parseCardAttrs(node.path("attrs")));
      case "mediaInline" -> new MediaInline(parseMediaAttrs(node.path("attrs")), parseMarks(node.get("marks")));
      case "date" -> new Date(
          AdfJson.text(node.path("attrs"), "timestamp"),
          AdfJson.text(node.path("attrs"), "localId"));
      case "emoji" -> new Emoji(
          AdfJson.text(node.path("attrs"), "id"),
          AdfJson.text(node.path("attrs"), "text"),
          AdfJson.text(node.path("attrs"), "shortName"));
      case "mention" -> new Mention(
          AdfJson.text(node.path("attrs"), "id"),
          mentionText(node.path("attrs")),
          AdfJson.text(node.path("attrs"), "userType"),
          AdfJson.text(node.path("attrs"), "accessLevel"),
          AdfJson.text(node.path("attrs"), "localId"));
      case "placeholder" -> new Placeholder(AdfJson.text(node.path("attrs"), "text", ""));
      case "status" -> new Status(
          AdfJson.text(node.path("attrs"), "text"),
          AdfJson.text(node.path("attrs"), "color"),
          AdfJson.text(node.path("attrs"), "style"),
          AdfJson.text(node.path("attrs"), "localId"));
      case "inlineExtension" -> parseInlineExtension(node);
      default -> new UnknownInline(type, rawJson(node));
    };
  }

  public List<AdfMark> parseMarks(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var marks = new ArrayList<AdfMark>(arrayNode.size());
    for (var mark : arrayNode) {
      if (mark == null || !mark.isObject()) {
        continue;
      }
      marks.add(parseMark(mark));
    }
    return marks;
  }

  public AdfMark parseMark(JsonNode node) {
    var type = AdfJson.text(node, "type", "");
    var attrs = node.path("attrs");
    return switch (type) {
      case "strong" -> new Strong();
      case "em" -> new Em();
      case "code" -> new Code();
      case "strike" -> new Strike();
      case "underline" -> new Underline();
      case "subsup" -> new SubSup(AdfJson.text(attrs, "type"));
      case "link" -> new Link(
          AdfJson.text(attrs, "href"),
          AdfJson.text(attrs, "title"),
          parseConfluenceMetadata(attrs.path("__confluenceMetadata")));
      case "textColor" -> new TextColor(AdfJson.text(attrs, "color"));
      case "backgroundColor" -> new BackgroundColor(AdfJson.text(attrs, "color"));
      case "alignment" -> new Alignment(AdfJson.text(attrs, "align"));
      case "indentation" -> new Indentation(attrs.path("level").asInt(0));
      case "fontSize" -> new FontSize(AdfJson.text(attrs, "fontSize"));
      case "border" -> new Border(AdfJson.text(attrs, "color"), AdfJson.text(attrs, "size"));
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
    var level = AdfJson.clampHeadingLevel(node.path("attrs").path("level").asInt(1));
    return new Heading(level, parseInlines(node.get("content")), parseMarks(node.get("marks")));
  }

  private CodeBlock parseCodeBlock(JsonNode node) {
    var language = AdfJson.text(node.path("attrs"), "language");
    var builder = new StringBuilder();
    var content = node.get("content");
    if (content != null && content.isArray()) {
      for (var child : content) {
        builder.append(AdfJson.text(child, "text", ""));
      }
    }
    return new CodeBlock(language, builder.toString());
  }

  private List<ListItem> parseListItems(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var items = new ArrayList<ListItem>(arrayNode.size());
    for (var child : arrayNode) {
      if (child == null || !child.isObject()) {
        continue;
      }
      if (!"listItem".equals(AdfJson.text(child, "type"))) {
        continue;
      }
      items.add(new ListItem(parseBlocks(child.get("content"))));
    }
    return items;
  }

  private OrderedList parseOrderedList(JsonNode node) {
    var order = node.path("attrs").path("order").asInt(1);
    return new OrderedList(order, parseListItems(node.get("content")));
  }

  private List<AdfBlock> parseTaskListItems(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var items = new ArrayList<AdfBlock>(arrayNode.size());
    for (var child : arrayNode) {
      if (child == null || !child.isObject()) {
        continue;
      }
      var type = AdfJson.text(child, "type", "");
      if (!"taskItem".equals(type) && !"blockTaskItem".equals(type)) {
        continue;
      }
      items.add(parseBlock(child));
    }
    return items;
  }

  private List<DecisionItem> parseDecisionItems(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var items = new ArrayList<DecisionItem>(arrayNode.size());
    for (var child : arrayNode) {
      if (child == null || !child.isObject()) {
        continue;
      }
      if (!"decisionItem".equals(AdfJson.text(child, "type"))) {
        continue;
      }
      items.add(
          new DecisionItem(
              AdfJson.text(child.path("attrs"), "state"), parseInlines(child.get("content"))));
    }
    return items;
  }

  private Table parseTable(JsonNode node) {
    var numberColumnEnabled = node.path("attrs").path("isNumberColumnEnabled").asBoolean(false);
    var rowsNode = node.get("content");
    if (rowsNode == null || !rowsNode.isArray() || rowsNode.isEmpty()) {
      return new Table(numberColumnEnabled, List.of());
    }
    var rows = new ArrayList<TableRow>(rowsNode.size());
    for (var rowNode : rowsNode) {
      if (rowNode == null || !rowNode.isObject()) {
        continue;
      }
      if (!"tableRow".equals(AdfJson.text(rowNode, "type"))) {
        continue;
      }
      rows.add(new TableRow(parseTableCells(rowNode.get("content"))));
    }
    return new Table(numberColumnEnabled, rows);
  }

  private List<TableCell> parseTableCells(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var cells = new ArrayList<TableCell>(arrayNode.size());
    for (var cellNode : arrayNode) {
      if (cellNode == null || !cellNode.isObject()) {
        continue;
      }
      var type = AdfJson.text(cellNode, "type", "");
      if (!"tableCell".equals(type) && !"tableHeader".equals(type)) {
        continue;
      }
      cells.add(parseTableCell(cellNode, "tableHeader".equals(type)));
    }
    return cells;
  }

  private TableCell parseTableCell(JsonNode node, boolean header) {
    var attrs = node.path("attrs");
    var colspan = attrs.path("colspan").asInt(1);
    var rowspan = attrs.path("rowspan").asInt(1);
    var background = AdfJson.text(attrs, "background");
    return new TableCell(header, colspan, rowspan, background, parseBlocks(node.get("content")));
  }

  private MediaSingle parseMediaSingle(JsonNode node) {
    var attrs = node.path("attrs");
    var layout = AdfJson.text(attrs, "layout");
    var widthType = AdfJson.text(attrs, "widthType");
    var width = AdfJson.text(attrs, "width");
    return new MediaSingle(layout, widthType, width, parseMediaBlocks(node.get("content")));
  }

  private List<AdfBlock> parseMediaBlocks(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var blocks = new ArrayList<AdfBlock>(arrayNode.size());
    for (var child : arrayNode) {
      if (child == null || !child.isObject()) {
        continue;
      }
      blocks.add(parseBlock(child));
    }
    return blocks;
  }

  private List<LayoutColumn> parseLayoutColumns(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
      return List.of();
    }
    var columns = new ArrayList<LayoutColumn>(arrayNode.size());
    for (var child : arrayNode) {
      if (child == null || !child.isObject()) {
        continue;
      }
      if (!"layoutColumn".equals(AdfJson.text(child, "type"))) {
        continue;
      }
      var width = child.path("attrs").path("width").asInt(0);
      columns.add(new LayoutColumn(width, parseBlocks(child.get("content"))));
    }
    return columns;
  }

  private Extension parseExtension(JsonNode node) {
    var attrs = node.path("attrs");
    return new Extension(
        AdfJson.text(attrs, "extensionType"),
        AdfJson.text(attrs, "extensionKey"),
        parseMacroParams(attrs.path("parameters").path("macroParams")));
  }

  private BodiedExtension parseBodiedExtension(JsonNode node) {
    var attrs = node.path("attrs");
    return new BodiedExtension(
        AdfJson.text(attrs, "extensionType"),
        AdfJson.text(attrs, "extensionKey"),
        parseMacroParams(attrs.path("parameters").path("macroParams")),
        parseBlocks(node.get("content")));
  }

  private InlineExtension parseInlineExtension(JsonNode node) {
    var attrs = node.path("attrs");
    return new InlineExtension(
        AdfJson.text(attrs, "extensionType"),
        AdfJson.text(attrs, "extensionKey"),
        parseMacroParams(attrs.path("parameters").path("macroParams")));
  }

  private CardAttrs parseCardAttrs(JsonNode attrs) {
    if (attrs == null || !attrs.isObject()) {
      return new CardAttrs(null, null, null, null, null);
    }
    var url = AdfJson.text(attrs, "url");
    var datasourceId = AdfJson.text(attrs.path("datasource"), "id");
    var localId = AdfJson.text(attrs, "localId");
    var title = AdfJson.text(attrs.path("data"), "title");
    var confluenceMetadata = parseConfluenceMetadata(attrs.path("__confluenceMetadata"));
    return new CardAttrs(url, datasourceId, localId, title, confluenceMetadata);
  }

  private MediaAttrs parseMediaAttrs(JsonNode attrs) {
    if (attrs == null || !attrs.isObject()) {
      return new MediaAttrs(null, null, null, null, null, null, null, null, null, null, null, null);
    }
    return new MediaAttrs(
        AdfJson.text(attrs, "type"),
        AdfJson.text(attrs, "id"),
        AdfJson.text(attrs, "localId"),
        AdfJson.text(attrs, "url"),
        AdfJson.text(attrs, "collection"),
        AdfJson.text(attrs, "alt"),
        AdfJson.text(attrs, "width"),
        AdfJson.text(attrs, "height"),
        AdfJson.text(attrs, "mediaType"),
        AdfJson.text(attrs, "__fileMimeType"),
        AdfJson.text(attrs, "__fileName"),
        AdfJson.text(attrs, "name"));
  }

  private ConfluenceMetadata parseConfluenceMetadata(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
      return ConfluenceMetadata.empty();
    }
    return new ConfluenceMetadata(
        AdfJson.text(node, "linkType"),
        AdfJson.text(node, "pageId"),
        AdfJson.text(node, "contentId"),
        AdfJson.text(node, "id"));
  }

  private String mentionText(JsonNode attrs) {
    var raw = AdfJson.text(attrs, "text");
    if (raw == null) {
      return "";
    }
    var trimmed = raw.strip();
    return trimmed;
  }

  private String rawJson(JsonNode node) {
    try {
      return mapper.writeValueAsString(node);
    } catch (RuntimeException exception) {
      return "{}";
    }
  }
}
