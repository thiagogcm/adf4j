package dev.nthings.adf4j.ast;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
///         bulletList(listItem("Faster search"), listItem("New exports")),
///         table(
///             tableRow(tableHeader("Task"), tableHeader("State")),
///             tableRow(tableCell("Search"), tableCell(paragraph(status("DONE",
// StatusColor.GREEN))))));
///
/// var json = AdfJson.write(document);
/// }
///
/// The signatures carry the schema's content model. Parameters name the `*Content` type a container
/// allows, so a heading in a `listItem` doesn't compile. A container the schema requires to be
/// non-empty takes its first child separately, and `mediaSingle` and `layoutSection` take exactly
/// the children they may hold. Closed attribute sets are enums. The `List` overloads are for
// content
/// built from data, where these counts can't be checked until a document is published.
///
/// Attributes a node doesn't require are set through `withX` methods on the record, such as
/// {@link TableCell#withColspan(int)}. The `String` shortcuts skip empty text, because the schema
/// forbids an empty text node.
///
/// `doc` fills in the `localId`s the schema requires, deriving each from the node rather than at
/// random, so rebuilding the same content writes the same JSON. A task or decision's id comes from
/// its content, not its state or position, and identical items are told apart by their order.
/// Nodes outside a `doc` have no id, and a `withLocalId` value is kept.
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

    /// The value ADF stores.
    public String value() {
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

    /// The value ADF stores.
    public String value() {
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

    /// The value ADF stores.
    public String value() {
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

    /// The value ADF stores.
    public String value() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  /// The `align` of an {@link Alignment} mark.
  public enum TextAlign {
    CENTER,
    END;

    /// The value ADF stores.
    public String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  // ---- document and text blocks ----

  public static AdfDocument doc(DocContent... content) {
    return doc(List.of(content));
  }

  public static AdfDocument doc(List<? extends DocContent> content) {
    return new AdfDocument(VERSION, new LocalIds().assign(content));
  }

  public static Paragraph paragraph(InlineContent... content) {
    return paragraph(List.of(content));
  }

  public static Paragraph paragraph(String text) {
    return new Paragraph(Attributes.empty(), inlines(text), List.of());
  }

  public static Paragraph paragraph(List<? extends InlineContent> content) {
    return new Paragraph(Attributes.empty(), List.<AdfInline>copyOf(content), List.of());
  }

  /// A heading of `level` 1 to 6.
  public static Heading heading(int level, InlineContent... content) {
    return new Heading(level(level), List.<AdfInline>of(content), List.of());
  }

  /// A heading of `level` 1 to 6.
  public static Heading heading(int level, String text) {
    return new Heading(level(level), inlines(text), List.of());
  }

  public static Blockquote blockquote(BlockquoteContent first, BlockquoteContent... rest) {
    return new Blockquote(Attributes.empty(), blocks(first, rest), List.of());
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

  public static Rule rule() {
    return new Rule(Attributes.empty(), List.of());
  }

  public static Expand expand(String title, ExpandContent first, ExpandContent... rest) {
    return new Expand(title(title), blocks(first, rest), List.of());
  }

  /// An {@link Expand} for inside a table cell, or another expand's content.
  public static NestedExpand nestedExpand(
      String title, NestedExpandContent first, NestedExpandContent... rest) {
    return new NestedExpand(title(title), blocks(first, rest), List.of());
  }

  // ---- lists ----

  public static BulletList bulletList(ListItem first, ListItem... rest) {
    return new BulletList(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static BulletList bulletList(List<ListItem> items) {
    return new BulletList(Attributes.empty(), List.<AdfBlock>copyOf(items), List.of());
  }

  public static OrderedList orderedList(ListItem first, ListItem... rest) {
    return new OrderedList(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static OrderedList orderedList(List<ListItem> items) {
    return new OrderedList(Attributes.empty(), List.<AdfBlock>copyOf(items), List.of());
  }

  public static ListItem listItem(ListItemContent first, ListItemContent... rest) {
    return new ListItem(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static ListItem listItem(String text) {
    return listItem(paragraph(text));
  }

  public static TaskList taskList(TaskListContent first, TaskListContent... rest) {
    return new TaskList(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static TaskList taskList(List<? extends TaskListContent> items) {
    return new TaskList(Attributes.empty(), List.<AdfBlock>copyOf(items), List.of());
  }

  public static TaskItem taskItem(TaskState state, InlineContent... content) {
    return new TaskItem(task(state), List.<AdfInline>of(content), List.of());
  }

  public static TaskItem taskItem(TaskState state, String text) {
    return new TaskItem(task(state), inlines(text), List.of());
  }

  public static BlockTaskItem blockTaskItem(
      TaskState state, BlockTaskItemContent first, BlockTaskItemContent... rest) {
    return new BlockTaskItem(task(state), blocks(first, rest), List.of());
  }

  public static DecisionList decisionList(DecisionItem first, DecisionItem... rest) {
    return new DecisionList(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static DecisionList decisionList(List<DecisionItem> items) {
    return new DecisionList(Attributes.empty(), List.<AdfBlock>copyOf(items), List.of());
  }

  public static DecisionItem decisionItem(InlineContent... content) {
    return new DecisionItem(decided(), List.<AdfInline>of(content), List.of());
  }

  public static DecisionItem decisionItem(String text) {
    return new DecisionItem(decided(), inlines(text), List.of());
  }

  // ---- tables ----

  public static Table table(TableRow first, TableRow... rest) {
    return new Table(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static Table table(List<TableRow> rows) {
    return new Table(Attributes.empty(), List.<AdfBlock>copyOf(rows), List.of());
  }

  public static TableRow tableRow(TableCell first, TableCell... rest) {
    return new TableRow(Attributes.empty(), blocks(first, rest), List.of());
  }

  public static TableRow tableRow(List<TableCell> cells) {
    return new TableRow(Attributes.empty(), List.<AdfBlock>copyOf(cells), List.of());
  }

  public static TableCell tableCell(TableCellContent first, TableCellContent... rest) {
    return new TableCell(false, Attributes.empty(), blocks(first, rest), List.of());
  }

  public static TableCell tableCell(String text) {
    return tableCell(paragraph(text));
  }

  public static TableCell tableHeader(TableCellContent first, TableCellContent... rest) {
    return new TableCell(true, Attributes.empty(), blocks(first, rest), List.of());
  }

  public static TableCell tableHeader(String text) {
    return tableHeader(paragraph(text));
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
    return new Caption(Attributes.empty(), List.<AdfInline>of(content), List.of());
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

  /// A column taking `width` percent of its section.
  public static LayoutColumn layoutColumn(
      double width, LayoutColumnContent first, LayoutColumnContent... rest) {
    return new LayoutColumn(
        Attributes.empty().with("width", width), blocks(first, rest), List.of());
  }

  /// An extension without a body. See {@link #macroParameters(Map)} for a Confluence macro's
  /// `parameters`.
  public static Extension extension(String type, String key, Map<String, ?> parameters) {
    return new Extension(extensionAttrs(type, key, parameters), List.of());
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

  // ---- inline nodes ----

  public static Text text(String text, AdfMark... marks) {
    return new Text(text, List.of(marks));
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

  /// Aligns a paragraph or heading. See {@link Paragraph#withMarks(AdfMark...)}.
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

  // The schema forbids an empty text node, so empty text is no node at all.
  private static List<AdfInline> inlines(String text) {
    return text.isEmpty() ? List.of() : List.of(text(text));
  }

  private static List<AdfBlock> blocks(AdfBlock first, AdfBlock[] rest) {
    var blocks = new ArrayList<AdfBlock>(rest.length + 1);
    blocks.add(first);
    blocks.addAll(Arrays.asList(rest));
    return blocks;
  }

  private static Attributes level(int level) {
    return Attributes.empty().with("level", level);
  }

  private static Attributes title(String title) {
    return Attributes.empty().with("title", title);
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

  private static Attributes extensionAttrs(String type, String key, Map<String, ?> parameters) {
    return Attributes.empty()
        .with("extensionType", type)
        .with("extensionKey", key)
        .with("parameters", parameters);
  }

  // One document's id assignment, in document order so an item's occurrence is stable. Each node is
  // rebuilt, because records are immutable and only the document sees every item.
  private static final class LocalIds {

    private final Map<String, Integer> occurrences = new HashMap<>();

    List<AdfBlock> assign(List<? extends AdfBlock> blocks) {
      return blocks.stream().map(this::assign).toList();
    }

    private AdfBlock assign(AdfBlock block) {
      return switch (block) {
        case TaskList n -> new TaskList(id(n, n.type()), assign(n.content()), n.marks());
        case DecisionList n -> new DecisionList(id(n, n.type()), assign(n.content()), n.marks());
        case TaskItem n -> new TaskItem(id(n, n.type() + n.content()), n.content(), n.marks());
        case DecisionItem n ->
            new DecisionItem(id(n, n.type() + n.content()), n.content(), n.marks());
        case BlockTaskItem n ->
            new BlockTaskItem(id(n, n.type() + n.content()), n.content(), n.marks());
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

    // A type-3 UUID, the shape the editor writes, of what the node is plus how many came before it.
    private Attributes id(AdfNode node, String identity) {
      if (node.attrs().text("localId") != null) {
        return node.attrs();
      }
      var occurrence = occurrences.merge(identity, 1, Integer::sum);
      var name = identity + "#" + occurrence;
      var id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
      return node.attrs().with("localId", id.toString());
    }
  }
}
