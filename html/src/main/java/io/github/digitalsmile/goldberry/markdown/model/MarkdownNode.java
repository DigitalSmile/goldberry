package io.github.digitalsmile.goldberry.markdown.model;

/// A node of a parsed Markdown document.
///
/// Sealed over two halves — [Block]s, which stack down the page, and [Inline]s,
/// which flow along a line — because CommonMark is, and because that is the
/// distinction every consumer of this tree needs first: a block decides layout, an
/// inline decides style.
///
/// The whole hierarchy is records with no identity and no mutable state, so a
/// document is a value: it can be cached, compared, held across a frame, or built
/// by hand in a test with no parser in sight. Walking it is a `switch` with
/// patterns rather than a visitor —
///
/// ```java
/// static int words(MarkdownNode node) {
///     return switch (node) {
///         case Text(var text) -> text.split("\\s+").length;
///         case Block block -> block.blocks().stream().mapToInt(Markdowns::words).sum();
///         …
///     };
/// }
/// ```
///
/// — which is the reason the hierarchy is sealed rather than merely shallow: a
/// node added here makes every exhaustive switch over it a compile error, and
/// "every renderer that forgot the new node" is otherwise a list nobody has.
public sealed interface MarkdownNode permits Block, Inline {}
