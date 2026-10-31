package dev.nthings.adf4j.internal.writer;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.CodeBlock;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.LayoutColumn;
import dev.nthings.adf4j.ast.LayoutSection;
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
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.ast.UnknownMark;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// Writes an {@link AdfDocument} as ADF JSON in Jackson tree mode. Empty `attrs` and `marks` are
/// omitted, and a container always writes `content` because several nodes require it. Stateless
/// and thread-safe.
public final class AdfJsonWriter {

  private final JsonMapper mapper;

  private AdfJsonWriter(JsonMapper mapper) {
    this.mapper = mapper;
  }

  public static AdfJsonWriter createDefault() {
    return new AdfJsonWriter(
        JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build());
  }

  public String write(AdfDocument document) {
    return mapper.writeValueAsString(element(document));
  }

  private JsonNode node(AdfNode node) {
    return switch (node) {
      case UnknownBlock unknown -> raw(unknown.rawJson());
      case UnknownInline unknown -> raw(unknown.rawJson());
      default -> element(node);
    };
  }

  private ObjectNode element(AdfNode node) {
    var json = mapper.createObjectNode();
    json.put("type", node.type());
    switch (node) {
      case AdfDocument document -> json.put("version", document.version());
      case Text text -> json.put("text", text.text());
      default -> {}
    }
    if (!node.attrs().isEmpty()) {
      json.set("attrs", object(node.attrs().values()));
    }
    if (isContainer(node)) {
      var content = json.putArray("content");
      node.content().forEach(child -> content.add(node(child)));
    }
    if (!node.marks().isEmpty()) {
      var marks = json.putArray("marks");
      node.marks().forEach(mark -> marks.add(mark(mark)));
    }
    return json;
  }

  private JsonNode mark(AdfMark mark) {
    if (mark instanceof UnknownMark unknown) {
      return raw(unknown.rawJson());
    }
    var json = mapper.createObjectNode();
    json.put("type", mark.type());
    if (!mark.attrs().isEmpty()) {
      json.set("attrs", object(mark.attrs().values()));
    }
    return json;
  }

  // Exhaustive (no default) so a new node type must decide whether it writes `content`.
  private static boolean isContainer(AdfNode node) {
    return switch (node) {
      case AdfDocument _,
          Blockquote _,
          BlockTaskItem _,
          BodiedExtension _,
          BodiedSyncBlock _,
          BulletList _,
          Caption _,
          CodeBlock _,
          DecisionItem _,
          DecisionList _,
          Expand _,
          ExtensionFrame _,
          Heading _,
          LayoutColumn _,
          LayoutSection _,
          ListItem _,
          MediaGroup _,
          MediaSingle _,
          MultiBodiedExtension _,
          NestedExpand _,
          OrderedList _,
          Panel _,
          Paragraph _,
          Table _,
          TableCell _,
          TableRow _,
          TaskItem _,
          TaskList _ ->
          true;
      case BlockCard _,
          Date _,
          EmbedCard _,
          Emoji _,
          Extension _,
          HardBreak _,
          InlineCard _,
          InlineExtension _,
          Media _,
          MediaInline _,
          Mention _,
          Placeholder _,
          Rule _,
          Status _,
          SyncBlock _,
          Text _,
          UnknownBlock _,
          UnknownInline _ ->
          false;
    };
  }

  private JsonNode raw(String rawJson) {
    try {
      return mapper.readTree(rawJson);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Unknown node holds invalid JSON: " + rawJson, exception);
    }
  }

  private ObjectNode object(Map<?, ?> values) {
    var json = mapper.createObjectNode();
    values.forEach((key, value) -> json.set(String.valueOf(key), value(value)));
    return json;
  }

  private ArrayNode array(List<?> values) {
    var json = mapper.createArrayNode();
    values.forEach(value -> json.add(value(value)));
    return json;
  }

  // Covers every value Attributes admits.
  private JsonNode value(@Nullable Object value) {
    var nodes = mapper.getNodeFactory();
    return switch (value) {
      case null -> nodes.nullNode();
      case String text -> nodes.stringNode(text);
      case Boolean bool -> nodes.booleanNode(bool);
      case Long number -> nodes.numberNode(number);
      case BigInteger number -> nodes.numberNode(number);
      case Double number -> nodes.numberNode(number);
      case BigDecimal number -> nodes.numberNode(number);
      case Map<?, ?> nested -> object(nested);
      case List<?> nested -> array(nested);
      default -> throw new IllegalStateException("Not a JSON value: " + value.getClass());
    };
  }
}
