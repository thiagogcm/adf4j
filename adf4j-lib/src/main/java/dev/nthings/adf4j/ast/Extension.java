package dev.nthings.adf4j.ast;

import java.util.List;

/// An atomic (bodyless) custom extension. Most Confluence macros parse to this. `extensionType`
/// is the namespace (e.g. `com.atlassian.confluence.macro.core`) and `extensionKey` the macro
/// name (e.g. `toc`, `excerpt`); `text` is the optional fallback the editor stored for display.
/// `macroParams` is the flattened `parameters.macroParams` value map that most macros use, while
/// `parameters` is the full raw `parameters` envelope. Some extensions keep their data outside
/// `macroParams`, and that is the only place it surfaces. Consumers extend rendering through an
/// `ExtensionRenderer`, which receives the node itself. For an extension with body content see
// {@link BodiedExtension}/{@link MultiBodiedExtension}; for the inline
/// form see {@link InlineExtension}.
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
