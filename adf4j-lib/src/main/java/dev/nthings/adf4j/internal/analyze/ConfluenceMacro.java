package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfExtension;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.metadata.ExcerptIncludeReference;
import dev.nthings.adf4j.metadata.PageTreeMacro;
import dev.nthings.adf4j.metadata.PageTreeReference;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// What an extension node means to adf4j, read from its type, key and parameters: the single place
/// a macro's parameters are normalized, shared by rendering and metadata extraction. The two phases
/// deliberately react to different subsets, and the render phase consults custom renderers before
/// classifying.
public sealed interface ConfluenceMacro {

  /// The media node equivalent to an editor-migration `inline-media-image` macro.
  record InlineMediaImage(Media media) implements ConfluenceMacro {}

  /// The modern chart app (`com.atlassian.chart`), with any key.
  record Chart() implements ConfluenceMacro {}

  /// The `pagetree` or `children` macro.
  record PageTree(PageTreeReference reference) implements ConfluenceMacro {}

  /// The `toc` macro.
  record Toc(TocLevelRange range) implements ConfluenceMacro {}

  /// The `anchor` macro; `id` is non-blank, or `null` when the macro carries none.
  record Anchor(@Nullable String id) implements ConfluenceMacro {}

  /// The `iframe` macro with its raw `src`.
  record Iframe(@Nullable String src) implements ConfluenceMacro {}

  /// The `viewpdf` macro.
  record ViewPdf() implements ConfluenceMacro {}

  /// The legacy `chart` or `chart:*` macro. With a body, any of these is a caption over its data
  /// table. Without one, `labelled` (the `chart:default` key) renders a label, and the rest fall
  /// back like an unsupported macro.
  record LegacyChart(boolean labelled) implements ConfluenceMacro {}

  /// The `excerpt-include` macro with a source page.
  record ExcerptInclude(ExcerptIncludeReference reference) implements ConfluenceMacro {}

  /// The `attachments` macro.
  record Attachments() implements ConfluenceMacro {}

  /// The `excerpt` macro; `name` is `null` for the unnamed excerpt.
  record Excerpt(@Nullable String name) implements ConfluenceMacro {}

  /// Anything else, including an `excerpt-include` with no source page and an `inline-media-image`
  /// with no media id.
  record Other() implements ConfluenceMacro {}

  static ConfluenceMacro of(AdfExtension extension) {
    return switch (extension.extensionType()) {
      case "com.atlassian.confluence.macro.core" ->
          macro(extension.extensionKey(), extension.macroParams());
      case "com.atlassian.chart" -> new Chart();
      case "com.atlassian.confluence.migration" ->
          "inline-media-image".equals(extension.extensionKey())
              ? inlineMediaImage(extension.parameters())
              : new Other();
      case null, default -> new Other();
    };
  }

  private static ConfluenceMacro macro(@Nullable String key, MacroParams params) {
    return switch (key) {
      case "pagetree" ->
          new PageTree(
              new PageTreeReference(
                  PageTreeMacro.PAGETREE, rootParam(params, "root"), params.values()));
      case "children" ->
          new PageTree(
              new PageTreeReference(
                  PageTreeMacro.CHILDREN, rootParam(params, "page"), params.values()));
      case "toc" -> new Toc(TocLevelRange.of(params));
      case "anchor" ->
          new Anchor(
              Strings.firstNonBlankStripped(params.value(""), params.value("legacyAnchorId")));
      case "iframe" -> new Iframe(params.value("src"));
      case "viewpdf" -> new ViewPdf();
      case "excerpt-include" -> excerptInclude(params);
      case "attachments" -> new Attachments();
      case "excerpt" -> new Excerpt(Strings.trimToNull(params.value("name")));
      case String name when name.equals("chart") || name.startsWith("chart:") ->
          new LegacyChart(name.equals("chart:default"));
      case null, default -> new Other();
    };
  }

  // A macro root parameter (trimmed), or null for a blank or "@keyword" root.
  private static @Nullable String rootParam(MacroParams params, String name) {
    var trimmed = Strings.trimToNull(params.value(name));
    return trimmed == null || trimmed.startsWith("@") ? null : trimmed;
  }

  // The source page is the macro's unnamed default parameter.
  private static ConfluenceMacro excerptInclude(MacroParams params) {
    var page = Strings.trimToNull(params.value(""));
    if (page == null) {
      return new Other();
    }
    var name = Strings.trimToNull(params.value("name"));
    return new ExcerptInclude(new ExcerptIncludeReference(page, name, params.values()));
  }

  // This migration macro stores the media identity directly under `parameters` (`id`, `collection`,
  // `width`, `height`), not under `macroParams`; it always wraps an image, so the media type is
  // fixed.
  private static ConfluenceMacro inlineMediaImage(Attributes parameters) {
    var id = stringValue(parameters, "id");
    if (id == null) {
      return new Other();
    }
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("type", "file");
    attrs.put("mediaType", "image");
    attrs.put("id", id);
    for (var key : List.of("collection", "alt", "width", "height")) {
      var value = stringValue(parameters, key);
      if (value != null) {
        attrs.put(key, value);
      }
    }
    return new InlineMediaImage(new Media(new Attributes(attrs), List.of()));
  }

  // A parameter as trimmed text (numbers stringified), or null when absent/blank.
  private static @Nullable String stringValue(Attributes parameters, String key) {
    var value = parameters.values().get(key);
    return switch (value) {
      case String text -> Strings.trimToNull(text);
      case Number number -> Strings.trimToNull(number.toString());
      case null, default -> null;
    };
  }
}
