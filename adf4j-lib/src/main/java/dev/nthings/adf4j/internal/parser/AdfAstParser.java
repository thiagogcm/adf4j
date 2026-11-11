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
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.result.Diagnostic;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
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
    var run = new Run(root);
    var document = run.document(root);
    return new ParsedDocument(document, run.issues());
  }

  // One document's traversal, so the report never crosses threads or documents.
  private final class Run {

    private final @Nullable JsonNode root;
    // Built on the first unpreserved construct: well-formed documents never need a path.
    private @Nullable Map<JsonNode, String> locations;
    private final List<String> paths = new ArrayList<>();
    private @Nullable String firstUnpreserved;

    Run(@Nullable JsonNode root) {
      this.root = root;
    }

    private static void index(JsonNode node, String path, Map<JsonNode, String> locations) {
      if (node.isObject()) {
        locations.put(node, path);
        for (var entry : node.properties())
          index(entry.getValue(), path + "/" + Strings.pointerToken(entry.getKey()), locations);
      } else if (node.isArray()) {
        locations.put(node, path);
        for (int i = 0; i < node.size(); i++) index(node.get(i), path + "/" + i, locations);
      }
    }

    private String path(JsonNode node) {
      var index = locations;
      if (index == null) {
        index = new IdentityHashMap<>();
        if (root != null) index(root, "", index);
        locations = index;
      }
      return index.getOrDefault(node, "");
    }

    private void unpreserved(String path, String description) {
      if (paths.isEmpty()) {
        firstUnpreserved = description;
      }
      paths.add(path);
    }

    List<Diagnostic> issues() {
      if (paths.isEmpty()) {
        return List.of();
      }
      return List.of(
          new Diagnostic(
                  Diagnostic.Code.UNPRESERVED_JSON,
                  paths.size()
                      + " malformed JSON construct(s) have no place in the AST and would be lost"
                      + " when writing the document, first: "
                      + firstUnpreserved)
              .at(paths));
    }

    AdfDocument document(@Nullable JsonNode root) {
      if (root == null || !root.isObject()) {
        return new AdfDocument(1, List.of());
      }
      checkKeys(root, "doc", ROOT_KEYS);
      var declared = root.get("version");
      var version = declared == null ? 1 : declared.asInt(1);
      if (declared != null
          && !(declared.isNumber()
              && declared.decimalValue().compareTo(new BigDecimal(version)) == 0)) {
        unpreserved("/version", "version is not an integer");
      }
      return new AdfDocument(version, blocks(root, "doc"));
    }

    private List<AdfBlock> blocks(JsonNode parent, String parentType) {
      return children(parent, parentType, "content", this::block);
    }

    private List<AdfMark> marks(JsonNode parent, String parentType) {
      return children(parent, parentType, "marks", this::mark);
    }

    // The field's object elements through fn; the single place array traversal lives.
    private <T> List<T> children(
        JsonNode parent, String parentType, String field, Function<JsonNode, T> fn) {
      var arrayNode = parent.get(field);
      if (arrayNode == null || isAbsent(arrayNode)) {
        return List.of();
      }
      if (!arrayNode.isArray()) {
        unpreserved(path(parent) + "/" + field, parentType + "." + field + " is not an array");
        return List.of();
      }
      var result = new ArrayList<T>(arrayNode.size());
      for (int i = 0; i < arrayNode.size(); i++) {
        var child = arrayNode.get(i);
        if (child.isObject()) {
          result.add(fn.apply(child));
        } else {
          unpreserved(
              path(parent) + "/" + field + "/" + i,
              parentType + "." + field + " has a non-object element");
        }
      }
      return result;
    }

    private AdfBlock withBlocks(JsonNode node, String type, ContainerFactory<AdfBlock> factory) {
      return factory.create(
          attrs(node, type, CONTAINER_KEYS), blocks(node, type), marks(node, type));
    }

    private AdfBlock withInlines(JsonNode node, String type, ContainerFactory<AdfInline> factory) {
      return factory.create(
          attrs(node, type, CONTAINER_KEYS),
          children(node, type, "content", this::inline),
          marks(node, type));
    }

    private <N> N leaf(
        JsonNode node, String type, BiFunction<Attributes, List<AdfMark>, N> factory) {
      return factory.apply(attrs(node, type, LEAF_KEYS), marks(node, type));
    }

    private AdfMark markOf(JsonNode node, String type, Function<Attributes, AdfMark> factory) {
      return factory.apply(attrs(node, type, MARK_KEYS));
    }

    AdfBlock block(JsonNode node) {
      var type = JsonFields.text(node, "type");
      return switch (type) {
        case "paragraph" -> withInlines(node, type, Paragraph::new);
        case "heading" -> withInlines(node, type, Heading::new);
        case "blockquote" -> withBlocks(node, type, Blockquote::new);
        case "codeBlock" -> withInlines(node, type, CodeBlock::new);
        case "panel" -> withBlocks(node, type, Panel::new);
        case "rule" -> leaf(node, type, Rule::new);
        case "bulletList" -> withBlocks(node, type, BulletList::new);
        case "orderedList" -> withBlocks(node, type, OrderedList::new);
        case "listItem" -> withBlocks(node, type, ListItem::new);
        case "taskList" -> withBlocks(node, type, TaskList::new);
        case "taskItem" -> withInlines(node, type, TaskItem::new);
        case "blockTaskItem" -> withBlocks(node, type, BlockTaskItem::new);
        case "decisionList" -> withBlocks(node, type, DecisionList::new);
        case "decisionItem" -> withInlines(node, type, DecisionItem::new);
        case "table" -> withBlocks(node, type, Table::new);
        case "tableRow" -> withBlocks(node, type, TableRow::new);
        case "tableCell" ->
            withBlocks(
                node, type, (attrs, content, marks) -> new TableCell(false, attrs, content, marks));
        case "tableHeader" ->
            withBlocks(
                node, type, (attrs, content, marks) -> new TableCell(true, attrs, content, marks));
        case "mediaSingle" -> withBlocks(node, type, MediaSingle::new);
        case "mediaGroup" -> withBlocks(node, type, MediaGroup::new);
        case "media" -> leaf(node, type, Media::new);
        case "caption" -> withInlines(node, type, Caption::new);
        case "expand" -> withBlocks(node, type, Expand::new);
        case "nestedExpand" -> withBlocks(node, type, NestedExpand::new);
        case "layoutSection" -> withBlocks(node, type, LayoutSection::new);
        case "layoutColumn" -> withBlocks(node, type, LayoutColumn::new);
        case "extension" -> leaf(node, type, Extension::new);
        case "bodiedExtension" -> withBlocks(node, type, BodiedExtension::new);
        case "multiBodiedExtension" -> withBlocks(node, type, MultiBodiedExtension::new);
        case "extensionFrame" -> withBlocks(node, type, ExtensionFrame::new);
        case "syncBlock" -> leaf(node, type, SyncBlock::new);
        case "bodiedSyncBlock" -> withBlocks(node, type, BodiedSyncBlock::new);
        case "blockCard" -> leaf(node, type, BlockCard::new);
        case "embedCard" -> leaf(node, type, EmbedCard::new);
        default -> new UnknownBlock(type, rawJson(node));
      };
    }

    AdfInline inline(JsonNode node) {
      var type = JsonFields.text(node, "type");
      return switch (type) {
        case "text" -> text(node);
        case "hardBreak" -> leaf(node, type, HardBreak::new);
        case "inlineCard" -> leaf(node, type, InlineCard::new);
        case "embedCard" -> leaf(node, type, EmbedCard::new);
        case "blockCard" -> leaf(node, type, BlockCard::new);
        case "mediaInline" -> leaf(node, type, MediaInline::new);
        case "date" -> leaf(node, type, Date::new);
        case "emoji" -> leaf(node, type, Emoji::new);
        case "mention" -> leaf(node, type, Mention::new);
        case "placeholder" -> leaf(node, type, Placeholder::new);
        case "status" -> leaf(node, type, Status::new);
        case "inlineExtension" -> leaf(node, type, InlineExtension::new);
        default -> new UnknownInline(type, rawJson(node));
      };
    }

    private Text text(JsonNode node) {
      checkKeys(node, "text", TEXT_KEYS);
      var text = node.get("text");
      if (text == null || !text.isString()) {
        unpreserved(path(node) + "/text", "text node without a string text");
      }
      return new Text(JsonFields.text(node, "text"), attributes(node, "text"), marks(node, "text"));
    }

    AdfMark mark(JsonNode node) {
      var type = JsonFields.text(node, "type");
      return switch (type) {
        case "strong" -> markOf(node, type, Strong::new);
        case "em" -> markOf(node, type, Em::new);
        case "code" -> markOf(node, type, Code::new);
        case "strike" -> markOf(node, type, Strike::new);
        case "underline" -> markOf(node, type, Underline::new);
        case "subsup" -> markOf(node, type, SubSup::new);
        case "link" -> markOf(node, type, Link::new);
        case "textColor" -> markOf(node, type, TextColor::new);
        case "backgroundColor" -> markOf(node, type, BackgroundColor::new);
        case "alignment" -> markOf(node, type, Alignment::new);
        case "indentation" -> markOf(node, type, Indentation::new);
        case "fontSize" -> markOf(node, type, FontSize::new);
        case "border" -> markOf(node, type, Border::new);
        case "annotation" -> markOf(node, type, Annotation::new);
        case "breakout" -> markOf(node, type, Breakout::new);
        case "fragment" -> markOf(node, type, Fragment::new);
        case "dataConsumer" -> markOf(node, type, DataConsumer::new);
        default -> new UnknownMark(type, rawJson(node));
      };
    }

    private Attributes attrs(JsonNode node, String type, Set<String> allowedKeys) {
      checkKeys(node, type, allowedKeys);
      return attributes(node, type);
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
          unpreserved(
              path(node) + "/" + Strings.pointerToken(key),
              type + " has unexpected key '" + key + "'");
        }
      }
    }

    private Attributes attributes(JsonNode owner, String type) {
      var attrs = owner.get("attrs");
      if (attrs == null || isAbsent(attrs)) {
        return Attributes.empty();
      }
      if (!attrs.isObject()) {
        unpreserved(path(owner) + "/attrs", type + ".attrs is not an object");
        return Attributes.empty();
      }
      return new Attributes(plainObject(attrs));
    }
  }

  // The canonical constructor of a block with `content` of C.
  @FunctionalInterface
  private interface ContainerFactory<C> {
    AdfBlock create(Attributes attrs, List<C> content, List<AdfMark> marks);
  }

  // Null and an empty container mean the same as a missing attrs, content or marks.
  private static boolean isAbsent(JsonNode node) {
    return node.isNull() || (node.isContainer() && node.isEmpty());
  }

  // Attributes drops null values, so these attrs write back as no attrs at all.
  private static boolean hasOnlyNulls(JsonNode attrs) {
    return attrs.isObject() && attrs.valueStream().allMatch(JsonNode::isNull);
  }

  // JSON nulls drop out of objects, leaving the key absent, but stay in arrays to keep indices.
  private static LinkedHashMap<String, Object> plainObject(JsonNode object) {
    var map = LinkedHashMap.<String, Object>newLinkedHashMap(object.size());
    for (var entry : object.properties()) {
      var value = plainValue(entry.getValue());
      if (value != null) {
        map.put(entry.getKey(), value);
      }
    }
    return map;
  }

  private static @Nullable Object plainValue(JsonNode node) {
    if (node.isNull()) {
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
