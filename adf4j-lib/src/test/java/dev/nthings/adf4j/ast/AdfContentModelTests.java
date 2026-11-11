package dev.nthings.adf4j.ast;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.testing.AdfJsonComparison;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/// Checks the `*Content` interfaces against `docs/spec/adf-schema.json`: each container's permitted
/// `type`s must equal those its schema `content` rules name, so a schema refresh that changes them
/// fails here and the API change gets reviewed. Where the schema leaves content open, the model
/// keeps the named types: `blockTaskItem` children after the second, and a `mediaSingle` inside a
/// `bodiedSyncBlock`. A rule narrowed in one `allOf` branch is out of reach: this compares the
/// union across a container's variants.
class AdfContentModelTests {

  private static final Map<String, Class<?>> CHILDREN_OF =
      Map.ofEntries(
          Map.entry("doc", DocContent.class),
          Map.entry("blockquote", BlockquoteContent.class),
          Map.entry("blockTaskItem", BlockTaskItemContent.class),
          Map.entry("bodiedExtension", BodiedExtensionContent.class),
          Map.entry("bodiedSyncBlock", BodiedSyncBlockContent.class),
          Map.entry("bulletList", ListItem.class),
          Map.entry("caption", CaptionContent.class),
          Map.entry("codeBlock", Text.class),
          Map.entry("decisionItem", InlineContent.class),
          Map.entry("decisionList", DecisionItem.class),
          Map.entry("expand", ExpandContent.class),
          Map.entry("heading", InlineContent.class),
          Map.entry("layoutColumn", LayoutColumnContent.class),
          Map.entry("layoutSection", LayoutColumn.class),
          Map.entry("listItem", ListItemContent.class),
          Map.entry("mediaGroup", Media.class),
          Map.entry("nestedExpand", NestedExpandContent.class),
          Map.entry("orderedList", ListItem.class),
          Map.entry("panel", PanelContent.class),
          Map.entry("paragraph", InlineContent.class),
          Map.entry("table", TableRow.class),
          Map.entry("tableCell", TableCellContent.class),
          Map.entry("tableHeader", TableCellContent.class),
          Map.entry("tableRow", TableCell.class),
          Map.entry("taskItem", InlineContent.class),
          Map.entry("taskList", TaskListContent.class));

  private final Map<String, JsonNode> definitions = new HashMap<>();

  @Test
  void every_container_permits_exactly_what_the_schema_allows() throws IOException {
    var schema =
        AdfJsonComparison.MAPPER.readTree(
            Files.readString(Path.of("../docs/spec/adf-schema.json")));
    schema.get("definitions").properties().forEach(e -> definitions.put(e.getKey(), e.getValue()));

    var fromSchema = new TreeMap<String, Set<String>>();
    definitions.forEach(
        (name, definition) -> {
          var types = ownTypes(definition, new HashSet<>());
          if (name.endsWith("_node") && types.size() == 1) {
            var specs = contentSpecs(definition);
            var children = new TreeSet<String>();
            specs.forEach(spec -> children.addAll(allowed(spec)));
            assertThat(specs.isEmpty() || !children.isEmpty())
                .as("%s has a content rule this derivation can read", name)
                .isTrue();
            if (!children.isEmpty()) {
              fromSchema.merge(
                  types.iterator().next(),
                  children,
                  (left, right) -> {
                    left.addAll(right);
                    return left;
                  });
            }
          }
        });

    var fromModel = new TreeMap<String, Set<String>>();
    CHILDREN_OF.forEach((container, type) -> fromModel.put(container, types(type)));
    // Adf.mediaSingle takes exactly a media and an optional caption.
    fromModel.put("mediaSingle", Set.of("caption", "media"));

    assertThat(fromModel).isEqualTo(fromSchema);
  }

  // Interfaces sharing accessors are not containers, so the derivation above never reaches
  // them. Their named types must still exist in the schema, which predates multiBodiedExtension.
  @Test
  void the_shared_accessor_interfaces_cover_their_node_types() throws IOException {
    var schema =
        AdfJsonComparison.MAPPER.readTree(
            Files.readString(Path.of("../docs/spec/adf-schema.json")));
    var schemaTypes = new TreeSet<String>();
    schema.get("definitions").properties().forEach(e -> definitions.put(e.getKey(), e.getValue()));
    definitions.values().forEach(d -> schemaTypes.addAll(ownTypes(d, new HashSet<>())));

    var fromModel =
        Map.of(
            AdfCard.class, types(AdfCard.class),
            AdfExtension.class, types(AdfExtension.class),
            AdfMedia.class, types(AdfMedia.class));

    assertThat(fromModel.get(AdfCard.class))
        .containsExactly("blockCard", "embedCard", "inlineCard");
    assertThat(fromModel.get(AdfExtension.class))
        .containsExactly("bodiedExtension", "extension", "inlineExtension", "multiBodiedExtension");
    assertThat(fromModel.get(AdfMedia.class)).containsExactly("media", "mediaInline");
    fromModel.values().forEach(types -> assertThat(schemaTypes).containsAll(without(types)));
    assertThat(AdfNode.class.getPermittedSubclasses())
        .contains(fromModel.keySet().toArray(new Class<?>[0]));
    assertThat(AdfUnknown.class.getPermittedSubclasses())
        .containsExactlyInAnyOrder(UnknownBlock.class, UnknownInline.class, UnknownMark.class);
  }

  @Test
  void every_mark_set_permits_exactly_what_the_schema_allows() throws IOException {
    var schema =
        AdfJsonComparison.MAPPER.readTree(
            Files.readString(Path.of("../docs/spec/adf-schema.json")));
    schema.get("definitions").properties().forEach(e -> definitions.put(e.getKey(), e.getValue()));

    assertThat(markTypes(TextMark.class))
        .isEqualTo(marksOf("formatted_text_inline_node", "code_inline_node"));
    assertThat(markTypes(ParagraphMark.class))
        .isEqualTo(
            marksOf(
                "paragraph_with_alignment_node",
                "paragraph_with_indentation_node",
                "paragraph_with_font_size_node"));
    assertThat(markTypes(HeadingMark.class))
        .isEqualTo(marksOf("heading_with_alignment_node", "heading_with_indentation_node"));
    assertThat(markTypes(MediaMark.class)).isEqualTo(marksOf("media_node"));
  }

  private static Set<String> markTypes(Class<?> type) {
    return Arrays.stream(type.getPermittedSubclasses())
        .map(mark -> mark.getSimpleName().toLowerCase(Locale.ROOT))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private Set<String> marksOf(String... nodes) {
    var marks = new TreeSet<String>();
    for (var node : nodes) {
      collectMarks(definitions.get(node), false, marks);
    }
    return marks;
  }

  private void collectMarks(JsonNode node, boolean underMarks, Set<String> marks) {
    if (underMarks && node.has("$ref")) {
      ownTypes(node, new HashSet<>()).forEach(mark -> marks.add(mark.toLowerCase(Locale.ROOT)));
    }
    node.properties()
        .forEach(
            entry ->
                collectMarks(
                    entry.getValue(), underMarks || entry.getKey().equals("marks"), marks));
    if (node.isArray()) {
      node.forEach(child -> collectMarks(child, underMarks, marks));
    }
  }

  private static Set<String> without(Set<String> types) {
    var known = new TreeSet<>(types);
    known.remove("multiBodiedExtension");
    return known;
  }

  // The JSON types a slot stands for. TableCell is one record for two types.
  private static Set<String> types(Class<?> type) {
    var classes =
        type.isSealed() ? Arrays.asList(type.getPermittedSubclasses()) : List.<Class<?>>of(type);
    return classes.stream()
        .flatMap(
            permitted ->
                permitted == TableCell.class
                    ? List.of("tableCell", "tableHeader").stream()
                    : List.of(jsonType(permitted)).stream())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static String jsonType(Class<?> record) {
    var name = record.getSimpleName();
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private JsonNode dereferenced(JsonNode node) {
    return node.has("$ref")
        ? definitions.get(node.get("$ref").asString().replace("#/definitions/", ""))
        : node;
  }

  private Set<String> ownTypes(JsonNode definition, Set<JsonNode> seen) {
    var node = dereferenced(definition);
    var types = new TreeSet<String>();
    if (!seen.add(node)) {
      return types;
    }
    node.path("properties").path("type").path("enum").forEach(t -> types.add(t.asString()));
    for (var key : List.of("anyOf", "oneOf", "allOf")) {
      node.path(key).forEach(option -> types.addAll(ownTypes(option, seen)));
    }
    return types;
  }

  private List<JsonNode> contentSpecs(JsonNode definition) {
    var node = dereferenced(definition);
    var specs = new ArrayList<JsonNode>();
    if (node.path("properties").has("content")) {
      specs.add(node.path("properties").get("content"));
    }
    for (var key : List.of("anyOf", "oneOf", "allOf")) {
      node.path(key).forEach(option -> specs.addAll(contentSpecs(option)));
    }
    return specs;
  }

  // Tuple `items` count too: blockTaskItem names only its first two positions. An
  // `additionalItems` schema names more; `true` and `false` name none.
  private Set<String> allowed(JsonNode contentSpec) {
    var spec = dereferenced(contentSpec);
    var types = new TreeSet<String>();
    if (spec.path("additionalItems").isObject()) {
      types.addAll(ownTypes(spec.get("additionalItems"), new HashSet<>()));
    }
    var items = spec.path("items");
    if (items.isArray()) {
      items.forEach(item -> types.addAll(ownTypes(item, new HashSet<>())));
    } else if (items.isObject()) {
      types.addAll(ownTypes(items, new HashSet<>()));
    }
    for (var key : List.of("anyOf", "oneOf", "allOf")) {
      spec.path(key).forEach(option -> types.addAll(allowed(option)));
    }
    return types;
  }
}
