package dev.nthings.adf4j.ast;

import java.util.List;

/// An atomic (bodyless) custom extension; most Confluence macros parse to this. `extensionType` is
/// the namespace (e.g. `com.atlassian.confluence.macro.core`), `extensionKey` the macro name (e.g.
/// `toc`), and `text` the optional fallback the editor stored for display. `macroParams` is the
/// flattened `parameters.macroParams` map most macros use; `parameters` is the full raw envelope.
/// Some extensions keep data outside `macroParams`, and only `parameters` exposes it. Rendering is
/// extended through an `ExtensionRenderer`, which receives the node. For body content see
/// {@link BodiedExtension} and {@link MultiBodiedExtension}; for the inline form, {@link
/// InlineExtension}.
public record Extension(Attributes attrs, List<AdfMark> marks)
    implements AdfBlock,
        AdfExtension,
        BlockTaskItemContent,
        BlockquoteContent,
        BodiedExtensionContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent,
        ListItemContent,
        NestedExpandContent,
        PanelContent,
        TableCellContent {

  public Extension {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "extension";
  }
}
