package dev.nthings.adf4j.internal.parser;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.Alignment;
import dev.nthings.adf4j.ast.Annotation;
import dev.nthings.adf4j.ast.Attributes;
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
import dev.nthings.adf4j.ast.Media;
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
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.Diagnostic.Severity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Maps ADF JSON onto the AST without interpreting it: each record keeps its node's `attrs`,
/// `content` and `marks`, and an unrecognized `type` keeps its raw JSON. What the AST has no place
/// for (non-object elements, unknown keys, a non-array `content`, a non-string `text`, ...) is
/// reported as one `UNPRESERVED_JSON` warning, because writing the document back would drop it.
/// Stateless and thread-safe; each parse keeps its own report.
public final class AdfAstParser {

  private static final String UNPRESERVED_JSON = "UNPRESERVED_JSON";

  private static final Logger log = LoggerFactory.getLogger(AdfAstParser.class);
  private static final Set<String> NODE_FIELDS = Set.of("attrs", "content", "marks");
  private static final Set<String> ROOT_KEYS = Set.of("type", "version", "content");
  private static final Set<String> LEAF_KEYS = Set.of("type", "attrs", "marks");
  private static final Set<String> CONTAINER_KEYS = Set.of("type", "attrs", "content", "marks");
  private static final Set<String> TEXT_KEYS = Set.of("type", "attrs", "text", "marks");
  private static final Set<String> MARK_KEYS = Set.of("type", "attrs");

  private final JsonMapper mapper;

  public AdfAstParser(JsonMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  /// A parsed document with what it could not preserve.
  public record ParsedDocument(AdfDocument document, List<Diagnostic> issues) {}

  public ParsedDocument parse(@Nullable JsonNode root) {
    var run = new Run();
    var document = run.document(root);
    return new ParsedDocument(document, run.issues());
  }

  // One document's traversal, so the report never crosses threads or documents.
  private final class Run {

    private int unpreserved;
    private @Nullable String firstUnpreserved;

    private void unpreserved(String description) {
      if (unpreserved++ == 0) {
        firstUnpreserved = description;
      }
    }

    List<Diagnostic> issues() {
      if (unpreserved == 0) {
        return List.of();
      }
      return List.of(
          new Diagnostic(
              UNPRESERVED_JSON,
              unpreserved
                  + " malformed JSON construct(s) have no place in the AST and would be lost when"
                  + " writing the document, first: "
                  + firstUnpreserved,
              null,
              Severity.WARNING));
    }

    AdfDocument document(@Nullable JsonNode root) {
      if (root == null || !root.isObject()) {
        return new AdfDocument(1, List.of());
      }
      checkKeys(root, "doc", ROOT_KEYS);
      var version = JsonFields.integer(root, "version", 1);
      var declared = root.get("version");
      if (declared != null
          && !(declared.isNumber()
              && declared.decimalValue().compareTo(new BigDecimal(version)) == 0)) {
        unpreserved("version is not an integer");
      }
      return new AdfDocument(version, blocks(root, "doc"));
    }

    private List<AdfBlock> blocks(JsonNode parent, String parentType) {
      return children(parent.get("content"), parentType + ".content", this::block);
    }

    private List<AdfInline> inlines(JsonNode parent, String parentType) {
      return children(parent.get("content"), parentType + ".content", this::inline);
    }

    private List<AdfMark> marks(JsonNode parent, String parentType) {
      return children(parent.get("marks"), parentType + ".marks", this::mark);
    }

    // The array's object elements through fn; the single place array traversal lives.
    private <T> List<T> children(
        @Nullable JsonNode arrayNode, String where, Function<JsonNode, T> fn) {
      if (arrayNode == null || isAbsent(arrayNode)) {
        return List.of();
      }
      if (!arrayNode.isArray()) {
        unpreserved(where + " is not an array");
        return List.of();
      }
      var result = new ArrayList<T>(arrayNode.size());
      for (var child : arrayNode) {
        if (child.isObject()) {
          result.add(fn.apply(child));
        } else {
          unpreserved(where + " has a non-object element");
        }
      }
      return result;
    }

    AdfBlock block(JsonNode node) {
      var type = JsonFields.text(node, "type", "");
      return switch (type) {
        case "paragraph" ->
            new Paragraph(attrs(node, type), inlines(node, type), marks(node, type));
        case "heading" -> new Heading(attrs(node, type), inlines(node, type), marks(node, type));
        case "blockquote" ->
            new Blockquote(attrs(node, type), blocks(node, type), marks(node, type));
        case "codeBlock" ->
            new CodeBlock(attrs(node, type), inlines(node, type), marks(node, type));
        case "panel" -> new Panel(attrs(node, type), blocks(node, type), marks(node, type));
        case "rule" -> new Rule(leafAttrs(node, type), marks(node, type));
        case "bulletList" ->
            new BulletList(attrs(node, type), blocks(node, type), marks(node, type));
        case "orderedList" ->
            new OrderedList(attrs(node, type), blocks(node, type), marks(node, type));
        case "listItem" -> new ListItem(attrs(node, type), blocks(node, type), marks(node, type));
        case "taskList" -> new TaskList(attrs(node, type), blocks(node, type), marks(node, type));
        case "taskItem" -> new TaskItem(attrs(node, type), inlines(node, type), marks(node, type));
        case "blockTaskItem" ->
            new BlockTaskItem(attrs(node, type), blocks(node, type), marks(node, type));
        case "decisionList" ->
            new DecisionList(attrs(node, type), blocks(node, type), marks(node, type));
        case "decisionItem" ->
            new DecisionItem(attrs(node, type), inlines(node, type), marks(node, type));
        case "table" -> new Table(attrs(node, type), blocks(node, type), marks(node, type));
        case "tableRow" -> new TableRow(attrs(node, type), blocks(node, type), marks(node, type));
        case "tableCell" ->
            new TableCell(false, attrs(node, type), blocks(node, type), marks(node, type));
        case "tableHeader" ->
            new TableCell(true, attrs(node, type), blocks(node, type), marks(node, type));
        case "mediaSingle" ->
            new MediaSingle(attrs(node, type), blocks(node, type), marks(node, type));
        case "mediaGroup" ->
            new MediaGroup(attrs(node, type), blocks(node, type), marks(node, type));
        case "media" -> new Media(leafAttrs(node, type), marks(node, type));
        case "caption" -> new Caption(attrs(node, type), inlines(node, type), marks(node, type));
        case "expand" -> new Expand(attrs(node, type), blocks(node, type), marks(node, type));
        case "nestedExpand" ->
            new NestedExpand(attrs(node, type), blocks(node, type), marks(node, type));
        case "layoutSection" ->
            new LayoutSection(attrs(node, type), blocks(node, type), marks(node, type));
        case "layoutColumn" ->
            new LayoutColumn(attrs(node, type), blocks(node, type), marks(node, type));
        case "extension" -> new Extension(leafAttrs(node, type), marks(node, type));
        case "bodiedExtension" ->
            new BodiedExtension(attrs(node, type), blocks(node, type), marks(node, type));
        case "multiBodiedExtension" ->
            new MultiBodiedExtension(attrs(node, type), blocks(node, type), marks(node, type));
        case "extensionFrame" ->
            new ExtensionFrame(attrs(node, type), blocks(node, type), marks(node, type));
        case "syncBlock" -> new SyncBlock(leafAttrs(node, type), marks(node, type));
        case "bodiedSyncBlock" ->
            new BodiedSyncBlock(attrs(node, type), blocks(node, type), marks(node, type));
        case "blockCard" -> new BlockCard(leafAttrs(node, type), marks(node, type));
        case "embedCard" -> new EmbedCard(leafAttrs(node, type), marks(node, type));
        default -> new UnknownBlock(type, rawJson(node));
      };
    }

    AdfInline inline(JsonNode node) {
      var type = JsonFields.text(node, "type", "");
      return switch (type) {
        case "text" -> text(node);
        case "hardBreak" -> new HardBreak(leafAttrs(node, type), marks(node, type));
        case "inlineCard" -> new InlineCard(leafAttrs(node, type), marks(node, type));
        case "embedCard" -> new EmbedCard(leafAttrs(node, type), marks(node, type));
        case "blockCard" -> new BlockCard(leafAttrs(node, type), marks(node, type));
        case "mediaInline" -> new MediaInline(leafAttrs(node, type), marks(node, type));
        case "date" -> new Date(leafAttrs(node, type), marks(node, type));
        case "emoji" -> new Emoji(leafAttrs(node, type), marks(node, type));
        case "mention" -> new Mention(leafAttrs(node, type), marks(node, type));
        case "placeholder" -> new Placeholder(leafAttrs(node, type), marks(node, type));
        case "status" -> new Status(leafAttrs(node, type), marks(node, type));
        case "inlineExtension" -> new InlineExtension(leafAttrs(node, type), marks(node, type));
        default -> new UnknownInline(type, rawJson(node));
      };
    }

    private Text text(JsonNode node) {
      checkKeys(node, "text", TEXT_KEYS);
      var text = JsonFields.field(node, "text");
      if (text == null || !text.isString()) {
        unpreserved("text node without a string text");
      }
      return new Text(
          JsonFields.text(node, "text", ""),
          attributes(node.get("attrs"), "text"),
          marks(node, "text"));
    }

    AdfMark mark(JsonNode node) {
      var type = JsonFields.text(node, "type", "");
      return switch (type) {
        case "strong" -> new Strong(markAttrs(node, type));
        case "em" -> new Em(markAttrs(node, type));
        case "code" -> new Code(markAttrs(node, type));
        case "strike" -> new Strike(markAttrs(node, type));
        case "underline" -> new Underline(markAttrs(node, type));
        case "subsup" -> new SubSup(markAttrs(node, type));
        case "link" -> new Link(markAttrs(node, type));
        case "textColor" -> new TextColor(markAttrs(node, type));
        case "backgroundColor" -> new BackgroundColor(markAttrs(node, type));
        case "alignment" -> new Alignment(markAttrs(node, type));
        case "indentation" -> new Indentation(markAttrs(node, type));
        case "fontSize" -> new FontSize(markAttrs(node, type));
        case "border" -> new Border(markAttrs(node, type));
        case "annotation" -> new Annotation(markAttrs(node, type));
        case "breakout" -> new Breakout(markAttrs(node, type));
        case "fragment" -> new Fragment(markAttrs(node, type));
        case "dataConsumer" -> new DataConsumer(markAttrs(node, type));
        default -> new UnknownMark(type, rawJson(node));
      };
    }

    private Attributes attrs(JsonNode node, String type) {
      checkKeys(node, type, CONTAINER_KEYS);
      return attributes(node.get("attrs"), type);
    }

    private Attributes leafAttrs(JsonNode node, String type) {
      checkKeys(node, type, LEAF_KEYS);
      return attributes(node.get("attrs"), type);
    }

    private Attributes markAttrs(JsonNode node, String type) {
      checkKeys(node, type, MARK_KEYS);
      return attributes(node.get("attrs"), type);
    }

    private void checkKeys(JsonNode node, String type, Set<String> allowed) {
      for (var property : node.properties()) {
        var key = property.getKey();
        var value = property.getValue();
        var keptOrEquivalent =
            allowed.contains(key)
                || value.isNull()
                || (key.equals("attrs") && hasOnlyNulls(value))
                || (NODE_FIELDS.contains(key) && isAbsent(value));
        if (!keptOrEquivalent) {
          unpreserved(type + " has unexpected key '" + key + "'");
        }
      }
    }

    private Attributes attributes(@Nullable JsonNode attrs, String type) {
      if (attrs == null || isAbsent(attrs)) {
        return Attributes.empty();
      }
      if (!attrs.isObject()) {
        unpreserved(type + ".attrs is not an object");
        return Attributes.empty();
      }
      return new Attributes(plainObject(attrs));
    }
  }

  // Null and an empty container mean the same as a missing attrs, content or marks.
  private static boolean isAbsent(JsonNode node) {
    return node.isNull() || node.isMissingNode() || (node.isContainer() && node.isEmpty());
  }

  // Attributes drops null values, so these attrs write back as no attrs at all.
  private static boolean hasOnlyNulls(JsonNode attrs) {
    return attrs.isObject() && attrs.valueStream().allMatch(JsonNode::isNull);
  }

  // JSON nulls drop out of objects, leaving the key absent, but stay in arrays to keep indices.
  private static LinkedHashMap<String, Object> plainObject(JsonNode object) {
    var map = new LinkedHashMap<String, Object>();
    for (var entry : object.properties()) {
      var value = plainValue(entry.getValue());
      if (value != null) {
        map.put(entry.getKey(), value);
      }
    }
    return map;
  }

  private static @Nullable Object plainValue(JsonNode node) {
    if (node.isNull() || node.isMissingNode()) {
      return null;
    }
    if (node.isObject()) {
      return plainObject(node);
    }
    if (node.isArray()) {
      var items = new ArrayList<@Nullable Object>(node.size());
      for (var child : node) {
        items.add(plainValue(child));
      }
      return items;
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    return node.isNumber() ? JsonFields.number(node) : node.stringValue();
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
