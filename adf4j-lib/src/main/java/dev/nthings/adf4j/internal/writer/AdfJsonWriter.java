package dev.nthings.adf4j.internal.writer;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.AdfUnknown;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// Writes an {@link AdfDocument} as ADF JSON. Empty `attrs` and `marks` are omitted; containers
/// always write `content`, which several nodes require. Stateless and thread-safe.
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
    return mapper.writeValueAsString(tree(document));
  }

  public JsonNode tree(AdfDocument document) {
    return element(document);
  }

  private JsonNode node(AdfNode node) {
    return node instanceof AdfUnknown unknown ? raw(unknown.rawJson()) : element(node);
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
      json.set("attrs", mapper.valueToTree(node.attrs().values()));
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
    if (mark instanceof AdfUnknown unknown) {
      return raw(unknown.rawJson());
    }
    var json = mapper.createObjectNode();
    json.put("type", mark.type());
    if (!mark.attrs().isEmpty()) {
      json.set("attrs", mapper.valueToTree(mark.attrs().values()));
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
}
