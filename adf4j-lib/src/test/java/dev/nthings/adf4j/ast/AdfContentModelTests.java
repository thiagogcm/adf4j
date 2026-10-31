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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/// Checks the `*Content` interfaces against `docs/spec/adf-schema.json`: for every container,
/// the `type`s its interface permits are exactly the `type`s the schema's `content` rules name.
/// A schema refresh that changes those rules fails here, so the API change is reviewed, not
/// implied. Where the schema leaves content open, the model stays with the named types:
/// `blockTaskItem` children after the second, and a `mediaSingle` inside a `bodiedSyncBlock`. A
/// rule narrowed only in one `allOf` branch is out of reach, because this compares the union
/// across a container's variants.
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
          Map.entry("mediaSingle", MediaSingleContent.class),
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

    assertThat(fromModel).isEqualTo(fromSchema);
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
