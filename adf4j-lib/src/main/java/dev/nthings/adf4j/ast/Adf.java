package dev.nthings.adf4j.ast;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Builds ADF documents in code. Every factory returns the immutable AST record for its node, so a
/// built document works with everything a parsed one does.
///
/// {@snippet :
/// import static dev.nthings.adf4j.ast.Adf.*;
///
/// var document =
///     doc(
///         heading(2, "Release notes"),
///         paragraph(text("Shipped by "), mention("5b10a2844c20165700ede21g", "@Ana"), text(".")),
///         panel(PanelType.WARNING, paragraph(text("Restart required", strong()))),
///         bulletList(listItem("Faster search"), listItem("New exports")));
///
/// var json = AdfJson.write(document);
/// }
///
/// The signatures carry as much of the schema as types can. Parameters name the `*Content` type a
/// container allows, so a heading in a `listItem` doesn't compile, and `TextMark` and its siblings
/// do the same for marks. A container the schema requires to be non-empty takes its first child
/// separately, and `mediaSingle` and `layoutSection` take exactly the children they may hold.
/// Closed attribute sets are enums.
///
/// Every container also takes a `List`, for content built from data. These overloads throw
/// `IllegalArgumentException` when a required child is missing, because an empty query result is
/// ordinary data and the alternative is a rejected document.
///
/// Attributes a node doesn't require are set through `withX` methods on the record, such as
/// {@link TableCell#withColspan(int)}. Empty text is no node at all, wherever it appears, because
/// the schema forbids it and Jira rejects a document containing it.
///
/// `doc` fills in the `localId`s the schema requires, deriving each from the node rather than at
/// random, so rebuilding the same content writes the same JSON. A task or decision's id comes from
/// its content, not its state or position, and identical items are told apart by their order. A
/// list's id comes from its order among lists. A `withLocalId` value is kept, and generated ids
/// avoid every other id on a known node. Nodes outside a `doc` have no id.
public final class Adf {

  private static final int VERSION = 1;

  private Adf() {}

  /// The `panelType` of a {@link Panel}.
  public enum PanelType {
    INFO,
    NOTE,
    TIP,
    WARNING,
    ERROR,
    SUCCESS,
    CUSTOM;

    String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /// The `state` of a {@link TaskItem} or {@link BlockTaskItem}.
  public enum TaskState {
    TODO,
    DONE
  }

  /// The `color` of a {@link Status}.
  public enum StatusColor {
    NEUTRAL,
    PURPLE,
    BLUE,
    RED,
    YELLOW,
    GREEN;

    String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /// The `layout` of a {@link MediaSingle} or {@link EmbedCard}.
  public enum MediaLayout {
    WIDE,
    FULL_WIDTH,
    CENTER,
    WRAP_RIGHT,
    WRAP_LEFT,
    ALIGN_END,
    ALIGN_START;

    String value() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  /// The `layout` of a {@link Table}.
  public enum TableLayout {
    DEFAULT,
    WIDE,
    FULL_WIDTH,
    CENTER,
    ALIGN_END,
    ALIGN_START;

    String value() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  /// The `align` of an {@link Alignment} mark.
  public enum TextAlign {
    CENTER,
    END;

    String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  // ---- document and text blocks ----

  public static AdfDocument doc(DocContent... content) {
    return doc(List.of(content));
  }

  public static AdfDocument doc(List<? extends DocContent> content) {
    return new AdfDocument(VERSION, withLocalIds(List.of(), content));
  }

  public static Paragraph paragraph(InlineContent... content) {
    return paragraph(List.of(content));
  }

  public static Paragraph paragraph(String text) {
    return new Paragraph(Attributes.empty(), inlines(text), List.of());
  }

  public static Paragraph paragraph(List<? extends InlineContent> content) {
    return new Paragraph(Attributes.empty(), inlines(content), List.of());
  }

  /// A heading of `level` 1 to 6.
  public static Heading heading(int level, InlineContent... content) {
    return heading(level, List.of(content));
  }

  /// A heading of `level` 1 to 6.
  public static Heading heading(int level, List<? extends InlineContent> content) {
    return new Heading(level(level), inlines(content), List.of());
  }

  /// A heading of `level` 1 to 6.
  public static Heading heading(int level, String text) {
    return new Heading(level(level), inlines(text), List.of());
  }

  public static Blockquote blockquote(BlockquoteContent first, BlockquoteContent... rest) {
    return new Blockquote(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static Blockquote blockquote(String text) {
    return blockquote(paragraph(text));
  }

  public static Blockquote blockquote(List<? extends BlockquoteContent> content) {
    return new Blockquote(Attributes.empty(), required("blockquote", content), List.of());
  }

  public static CodeBlock codeBlock(String code) {
    return new CodeBlock(Attributes.empty(), inlines(code), List.of());
  }

  public static CodeBlock codeBlock(String language, String code) {
    return new CodeBlock(Attributes.empty().with("language", language), inlines(code), List.of());
  }

  public static Panel panel(PanelType type, PanelContent first, PanelContent... rest) {
    var attrs = Attributes.empty().with("panelType", type.value());
    return new Panel(attrs, blocks(first, rest), List.of());
  }

  public static Panel panel(PanelType type, String text) {
    return panel(type, paragraph(text));
  }

  public static Panel panel(PanelType type, List<? extends PanelContent> content) {
    var attrs = Attributes.empty().with("panelType", type.value());
    return new Panel(attrs, required("panel", content), List.of());
  }

  public static Rule rule() {
    return new Rule(Attributes.empty(), List.of());
  }

  public static Expand expand(String title, ExpandContent first, ExpandContent... rest) {
    return new Expand(title(title), blocks(first, rest), List.of());
  }

  public static Expand expand(String title, String text) {
    return expand(title, paragraph(text));
  }

  public static Expand expand(String title, List<? extends ExpandContent> content) {
    return new Expand(title(title), required("expand", content), List.of());
  }

  /// An {@link Expand} for inside a table cell, or another expand's content.
  public static NestedExpand nestedExpand(
      String title, NestedExpandContent first, NestedExpandContent... rest) {
    return new NestedExpand(title(title), blocks(first, rest), List.of());
  }

  /// An {@link Expand} for inside a table cell, or another expand's content.
  public static NestedExpand nestedExpand(String title, String text) {
    return nestedExpand(title, paragraph(text));
  }

  /// An {@link Expand} for inside a table cell, or another expand's content.
  public static NestedExpand nestedExpand(
      String title, List<? extends NestedExpandContent> content) {
    return new NestedExpand(title(title), required("nestedExpand", content), List.of());
  }

  // ---- lists ----

  public static BulletList bulletList(ListItem first, ListItem... rest) {
    return new BulletList(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static BulletList bulletList(List<ListItem> items) {
    return new BulletList(Attributes.empty(), required("bulletList", items), List.of());
  }

  public static OrderedList orderedList(ListItem first, ListItem... rest) {
    return new OrderedList(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static OrderedList orderedList(List<ListItem> items) {
    return new OrderedList(Attributes.empty(), required("orderedList", items), List.of());
  }

  public static ListItem listItem(ListItemContent first, ListItemContent... rest) {
    return new ListItem(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static ListItem listItem(String text) {
    return listItem(paragraph(text));
  }

  public static ListItem listItem(List<? extends ListItemContent> content) {
    return new ListItem(Attributes.empty(), required("listItem", content), List.of());
  }

  public static TaskList taskList(TaskListContent first, TaskListContent... rest) {
    return new TaskList(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static TaskList taskList(List<? extends TaskListContent> items) {
    return new TaskList(Attributes.empty(), required("taskList", items), List.of());
  }

  public static TaskItem taskItem(TaskState state, InlineContent... content) {
    return taskItem(state, List.of(content));
  }

  public static TaskItem taskItem(TaskState state, List<? extends InlineContent> content) {
    return new TaskItem(task(state), inlines(content), List.of());
  }

  public static TaskItem taskItem(TaskState state, String text) {
    return new TaskItem(task(state), inlines(text), List.of());
  }

  public static BlockTaskItem blockTaskItem(
      TaskState state, BlockTaskItemContent first, BlockTaskItemContent... rest) {
    return new BlockTaskItem(task(state), blocks(first, rest), List.of());
  }

  public static BlockTaskItem blockTaskItem(
      TaskState state, List<? extends BlockTaskItemContent> content) {
    return new BlockTaskItem(task(state), required("blockTaskItem", content), List.of());
  }

  public static DecisionList decisionList(DecisionItem first, DecisionItem... rest) {
    return new DecisionList(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static DecisionList decisionList(List<DecisionItem> items) {
    return new DecisionList(Attributes.empty(), required("decisionList", items), List.of());
  }

  public static DecisionItem decisionItem(InlineContent... content) {
    return decisionItem(List.of(content));
  }

  public static DecisionItem decisionItem(List<? extends InlineContent> content) {
    return new DecisionItem(decided(), inlines(content), List.of());
  }

  public static DecisionItem decisionItem(String text) {
    return new DecisionItem(decided(), inlines(text), List.of());
  }

  // ---- tables ----

  public static Table table(TableRow first, TableRow... rest) {
    return new Table(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static Table table(List<TableRow> rows) {
    return new Table(Attributes.empty(), required("table", rows), List.of());
  }

  public static TableRow tableRow(TableCell first, TableCell... rest) {
    return new TableRow(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static TableRow tableRow(List<TableCell> cells) {
    return new TableRow(Attributes.empty(), required("tableRow", cells), List.of());
  }

  public static TableCell tableCell(TableCellContent first, TableCellContent... rest) {
    return new TableCell(false, Attributes.empty(), blocks(first, rest), List.of());
  }

  public static TableCell tableCell(String text) {
    return tableCell(paragraph(text));
  }

  public static TableCell tableCell(List<? extends TableCellContent> content) {
    return new TableCell(false, Attributes.empty(), required("tableCell", content), List.of());
  }

  public static TableCell tableHeader(TableCellContent first, TableCellContent... rest) {
    return new TableCell(true, Attributes.empty(), blocks(first, rest), List.of());
  }

  public static TableCell tableHeader(String text) {
    return tableHeader(paragraph(text));
  }

  public static TableCell tableHeader(List<? extends TableCellContent> content) {
    return new TableCell(true, Attributes.empty(), required("tableHeader", content), List.of());
  }

  // ---- media and cards ----

  /// A centered image or file.
  public static MediaSingle mediaSingle(Media media) {
    return new MediaSingle(center(), List.of(media), List.of());
  }

  /// A centered image or file with a caption.
  public static MediaSingle mediaSingle(Media media, Caption caption) {
    return new MediaSingle(center(), List.of(media, caption), List.of());
  }

  public static MediaGroup mediaGroup(Media first, Media... rest) {
    return new MediaGroup(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static MediaGroup mediaGroup(List<Media> items) {
    return new MediaGroup(Attributes.empty(), required("mediaGroup", items), List.of());
  }

  /// A file held by the media service.
  public static Media media(String id, String collection) {
    return new Media(file(id, collection), List.of());
  }

  /// An image at a URL.
  public static Media externalMedia(String url) {
    return new Media(Attributes.empty().with("type", "external").with("url", url), List.of());
  }

  public static MediaInline mediaInline(String id, String collection) {
    return new MediaInline(file(id, collection), List.of());
  }

  public static Caption caption(CaptionContent... content) {
    return caption(List.of(content));
  }

  public static Caption caption(List<? extends CaptionContent> content) {
    return new Caption(Attributes.empty(), inlines(content), List.of());
  }

  public static Caption caption(String text) {
    return new Caption(Attributes.empty(), inlines(text), List.of());
  }

  public static BlockCard blockCard(String url) {
    return new BlockCard(url(url), List.of());
  }

  public static EmbedCard embedCard(String url, MediaLayout layout) {
    return new EmbedCard(url(url).with("layout", layout.value()), List.of());
  }

  public static InlineCard inlineCard(String url) {
    return new InlineCard(url(url), List.of());
  }

  // ---- layout, extensions and sync blocks ----

  public static LayoutSection layoutSection(LayoutColumn first, LayoutColumn second) {
    return new LayoutSection(Attributes.empty(), List.of(first, second), List.of());
  }

  public static LayoutSection layoutSection(
      LayoutColumn first, LayoutColumn second, LayoutColumn third) {
    return new LayoutSection(Attributes.empty(), List.of(first, second, third), List.of());
  }

  /// A section of two or three columns, the only counts the schema allows.
  public static LayoutSection layoutSection(List<LayoutColumn> columns) {
    if (columns.size() < 2 || columns.size() > 3) {
      throw new IllegalArgumentException(
          "layoutSection needs 2 or 3 columns, got " + columns.size());
    }
    return new LayoutSection(Attributes.empty(), List.copyOf(columns), List.of());
  }

  /// A column taking `width` percent of its section.
  public static LayoutColumn layoutColumn(
      double width, LayoutColumnContent first, LayoutColumnContent... rest) {
    return new LayoutColumn(width(width), blocks(first, rest), List.of());
  }

  /// A column taking `width` percent of its section.
  public static LayoutColumn layoutColumn(
      double width, List<? extends LayoutColumnContent> content) {
    return new LayoutColumn(width(width), required("layoutColumn", content), List.of());
  }

  public static Extension extension(String type, String key) {
    return new Extension(extensionAttrs(type, key), List.of());
  }

  /// An extension without a body. `parameters` are written with their keys sorted, so a `Map.of`
  /// writes the same JSON on every run. See {@link #macroParameters(Map)} for a Confluence macro.
  public static Extension extension(String type, String key, Map<String, ?> parameters) {
    return new Extension(extensionAttrs(type, key, parameters), List.of());
  }

  public static BodiedExtension bodiedExtension(
      String type, String key, BodiedExtensionContent first, BodiedExtensionContent... rest) {
    return new BodiedExtension(extensionAttrs(type, key), blocks(first, rest), List.of());
  }

  public static BodiedExtension bodiedExtension(
      String type,
      String key,
      Map<String, ?> parameters,
      BodiedExtensionContent first,
      BodiedExtensionContent... rest) {
    return new BodiedExtension(
        extensionAttrs(type, key, parameters), blocks(first, rest), List.of());
  }

  public static BodiedExtension bodiedExtension(
      String type,
      String key,
      Map<String, ?> parameters,
      List<? extends BodiedExtensionContent> content) {
    return new BodiedExtension(
        extensionAttrs(type, key, parameters), required("bodiedExtension", content), List.of());
  }

  public static InlineExtension inlineExtension(String type, String key) {
    return new InlineExtension(extensionAttrs(type, key), List.of());
  }

  public static InlineExtension inlineExtension(
      String type, String key, Map<String, ?> parameters) {
    return new InlineExtension(extensionAttrs(type, key, parameters), List.of());
  }

  /// The `parameters` of a Confluence macro, which keeps each value in a `{"value": ...}` envelope
  /// under `macroParams`.
  public static Map<String, Object> macroParameters(Map<String, String> values) {
    var macroParams = LinkedHashMap.<String, Object>newLinkedHashMap(values.size());
    values.forEach((name, value) -> macroParams.put(name, Map.of("value", value)));
    return Map.of("macroParams", macroParams);
  }

  public static SyncBlock syncBlock(String resourceId) {
    return new SyncBlock(sync(resourceId), List.of());
  }

  public static BodiedSyncBlock bodiedSyncBlock(
      String resourceId, BodiedSyncBlockContent first, BodiedSyncBlockContent... rest) {
    return new BodiedSyncBlock(sync(resourceId), blocks(first, rest), List.of());
  }

  public static BodiedSyncBlock bodiedSyncBlock(
      String resourceId, List<? extends BodiedSyncBlockContent> content) {
    return new BodiedSyncBlock(sync(resourceId), required("bodiedSyncBlock", content), List.of());
  }

  // ---- inline nodes ----

  /// A run of text. The schema allows `code` only alongside `link` and `annotation`.
  public static Text text(String text, TextMark... marks) {
    return new Text(text, List.<AdfMark>of(marks));
  }

  public static HardBreak hardBreak() {
    return new HardBreak(Attributes.empty(), List.of());
  }

  public static Mention mention(String id) {
    return new Mention(Attributes.empty().with("id", id), List.of());
  }

  /// A mention displaying `text`, usually `@Name`.
  public static Mention mention(String id, String text) {
    return new Mention(Attributes.empty().with("id", id).with("text", text), List.of());
  }

  /// An emoji by short name, such as `:smile:`.
  public static Emoji emoji(String shortName) {
    return new Emoji(Attributes.empty().with("shortName", shortName), List.of());
  }

  public static Date date(LocalDate date) {
    var millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    return new Date(Attributes.empty().with("timestamp", Long.toString(millis)), List.of());
  }

  public static Status status(String text, StatusColor color) {
    var attrs = Attributes.empty().with("text", text).with("color", color.value());
    return new Status(attrs, List.of());
  }

  public static Placeholder placeholder(String text) {
    return new Placeholder(Attributes.empty().with("text", text), List.of());
  }

  // ---- marks ----

  public static Strong strong() {
    return new Strong(Attributes.empty());
  }

  public static Em em() {
    return new Em(Attributes.empty());
  }

  public static Code code() {
    return new Code(Attributes.empty());
  }

  public static Strike strike() {
    return new Strike(Attributes.empty());
  }

  public static Underline underline() {
    return new Underline(Attributes.empty());
  }

  public static SubSup sub() {
    return new SubSup(Attributes.empty().with("type", "sub"));
  }

  public static SubSup sup() {
    return new SubSup(Attributes.empty().with("type", "sup"));
  }

  public static Link link(String href) {
    return new Link(Attributes.empty().with("href", href));
  }

  public static Link link(String href, String title) {
    return new Link(Attributes.empty().with("href", href).with("title", title));
  }

  /// Text color, as `#rrggbb`.
  public static TextColor textColor(String color) {
    return new TextColor(Attributes.empty().with("color", color));
  }

  /// Text background color, as `#rrggbb`.
  public static BackgroundColor backgroundColor(String color) {
    return new BackgroundColor(Attributes.empty().with("color", color));
  }

  /// Aligns a paragraph or heading, through its `withMarks`.
  public static Alignment alignment(TextAlign align) {
    return new Alignment(Attributes.empty().with("align", align.value()));
  }

  /// Indents a paragraph or heading by `level` 1 to 6.
  public static Indentation indentation(int level) {
    return new Indentation(level(level));
  }

  /// Small text, for a paragraph.
  public static FontSize small() {
    return new FontSize(Attributes.empty().with("fontSize", "small"));
  }

  /// A border around media, `size` 1 to 3 pixels.
  public static Border border(int size, String color) {
    return new Border(Attributes.empty().with("size", size).with("color", color));
  }

  /// An inline comment anchor.
  public static Annotation annotation(String id) {
    return new Annotation(
        Attributes.empty().with("id", id).with("annotationType", "inlineComment"));
  }

  // ---- attributes and children ----

  // The schema forbids empty text, and Jira rejects it, so empty text is no node at all.
  private static List<AdfInline> inlines(String text) {
    return text.isEmpty() ? List.of() : List.of(text(text));
  }

  private static List<AdfInline> inlines(List<? extends AdfInline> content) {
    return content.stream().filter(node -> !isEmptyText(node)).map(AdfInline.class::cast).toList();
  }

  private static boolean isEmptyText(AdfInline node) {
    return switch (node) {
      case Text text -> text.text().isEmpty();
      case Status status -> status.attrs().text("text", "").isEmpty();
      default -> false;
    };
  }

  private static List<AdfBlock> blocks(AdfBlock first, AdfBlock[] rest) {
    var blocks = new ArrayList<AdfBlock>(rest.length + 1);
    blocks.add(first);
    blocks.addAll(Arrays.asList(rest));
    return blocks;
  }

  private static List<AdfBlock> required(String node, List<? extends AdfBlock> content) {
    if (content.isEmpty()) {
      throw new IllegalArgumentException(node + " needs at least one child");
    }
    return List.copyOf(content);
  }

  private static Attributes level(int level) {
    return Attributes.empty().with("level", level);
  }

  private static Attributes title(String title) {
    return Attributes.empty().with("title", title);
  }

  private static Attributes width(double width) {
    return Attributes.empty().with("width", width);
  }

  private static Attributes url(String url) {
    return Attributes.empty().with("url", url);
  }

  private static Attributes center() {
    return Attributes.empty().with("layout", MediaLayout.CENTER.value());
  }

  private static Attributes file(String id, String collection) {
    return Attributes.empty().with("type", "file").with("id", id).with("collection", collection);
  }

  private static Attributes task(TaskState state) {
    return Attributes.empty().with("state", state.name());
  }

  private static Attributes decided() {
    return Attributes.empty().with("state", "DECIDED");
  }

  private static Attributes sync(String resourceId) {
    return Attributes.empty().with("resourceId", resourceId);
  }

  private static Attributes extensionAttrs(String type, String key) {
    return Attributes.empty().with("extensionType", type).with("extensionKey", key);
  }

  private static Attributes extensionAttrs(String type, String key, Map<String, ?> parameters) {
    return extensionAttrs(type, key).with("parameters", sorted(parameters));
  }

  // Map.of iterates in an order salted per JVM run, which would reach the written JSON.
  private static Map<String, @Nullable Object> sorted(Map<?, ?> map) {
    var sorted = new TreeMap<String, @Nullable Object>();
    map.forEach((key, value) -> sorted.put(String.valueOf(key), sortedValue(value)));
    return sorted;
  }

  private static @Nullable Object sortedValue(@Nullable Object value) {
    return switch (value) {
      case Map<?, ?> map -> sorted(map);
      case List<?> list -> list.stream().map(Adf::sortedValue).toList();
      case null, default -> value;
    };
  }

  /// `added`, with `localId`s filled in that avoid any id in `existing` or `added`.
  static List<AdfBlock> withLocalIds(
      List<? extends AdfBlock> existing, List<? extends AdfBlock> added) {
    var ids = new LocalIds();
    existing.forEach(ids::reserve);
    added.forEach(ids::reserve);
    return ids.assign(added);
  }

  // One document's id assignment, in document order so an item's occurrence is stable. Each node is
  // rebuilt, because records are immutable and only the document sees every item.
  private static final class LocalIds {

    private final Set<String> used = new HashSet<>();
    private final Map<String, Integer> occurrences = new HashMap<>();

    void reserve(AdfNode node) {
      var id = node.attrs().text("localId");
      if (id != null) {
        used.add(id);
      }
      node.content().forEach(this::reserve);
    }

    List<AdfBlock> assign(List<? extends AdfBlock> blocks) {
      return blocks.stream().map(this::assign).toList();
    }

    private AdfBlock assign(AdfBlock block) {
      return switch (block) {
        case TaskList n -> new TaskList(id(n, n.type()), assign(n.content()), n.marks());
        case DecisionList n -> new DecisionList(id(n, n.type()), assign(n.content()), n.marks());
        case TaskItem n -> new TaskItem(id(n, identity(n)), n.content(), n.marks());
        case DecisionItem n -> new DecisionItem(id(n, identity(n)), n.content(), n.marks());
        case BlockTaskItem n -> new BlockTaskItem(id(n, identity(n)), n.content(), n.marks());
        case SyncBlock n -> new SyncBlock(id(n, n.type() + n.resourceId()), n.marks());
        case BodiedSyncBlock n ->
            new BodiedSyncBlock(id(n, n.type() + n.resourceId()), assign(n.content()), n.marks());
        case Blockquote n -> new Blockquote(n.attrs(), assign(n.content()), n.marks());
        case BulletList n -> new BulletList(n.attrs(), assign(n.content()), n.marks());
        case OrderedList n -> new OrderedList(n.attrs(), assign(n.content()), n.marks());
        case ListItem n -> new ListItem(n.attrs(), assign(n.content()), n.marks());
        case Panel n -> new Panel(n.attrs(), assign(n.content()), n.marks());
        case Expand n -> new Expand(n.attrs(), assign(n.content()), n.marks());
        case NestedExpand n -> new NestedExpand(n.attrs(), assign(n.content()), n.marks());
        case Table n -> new Table(n.attrs(), assign(n.content()), n.marks());
        case TableRow n -> new TableRow(n.attrs(), assign(n.content()), n.marks());
        case TableCell n -> new TableCell(n.header(), n.attrs(), assign(n.content()), n.marks());
        case LayoutSection n -> new LayoutSection(n.attrs(), assign(n.content()), n.marks());
        case LayoutColumn n -> new LayoutColumn(n.attrs(), assign(n.content()), n.marks());
        case BodiedExtension n -> new BodiedExtension(n.attrs(), assign(n.content()), n.marks());
        default -> block;
      };
    }

    private Attributes id(AdfNode node, String identity) {
      if (node.attrs().text("localId") != null) {
        return node.attrs();
      }
      String id;
      do {
        var occurrence = occurrences.merge(identity, 1, Integer::sum);
        var name = identity + "#" + occurrence;
        id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
      } while (!used.add(id));
      return node.attrs().with("localId", id);
    }

    // What an item says: its children, not its own state, which ticking it changes.
    private static String identity(AdfNode node) {
      var identity = new StringBuilder(node.type());
      node.content().forEach(child -> describe(child, identity));
      return identity.toString();
    }

    // Spelled out rather than toString, so a generated id doesn't depend on how a record prints.
    private static void describe(AdfNode node, StringBuilder out) {
      out.append('(').append(node.type()).append(sorted(node.attrs().values()));
      switch (node) {
        case Text text -> out.append(text.text());
        case UnknownBlock unknown -> out.append(unknown.rawJson());
        case UnknownInline unknown -> out.append(unknown.rawJson());
        default -> {}
      }
      for (var mark : node.marks()) {
        out.append(mark.type()).append(sorted(mark.attrs().values()));
      }
      node.content().forEach(child -> describe(child, out));
      out.append(')');
    }
  }
}
