package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;

/// Something that stacks down the page — a paragraph, a heading, a list, a table.
///
/// Every block has [#children()], which is what makes a generic walk possible; the
/// ones whose children are inline content say so by returning them as [Inline]s,
/// because a paragraph holds words and a block quote holds paragraphs and the
/// difference matters to a renderer.
public sealed interface Block extends MarkdownNode
        permits Document,
                Heading,
                Paragraph,
                Quote,
                BulletList,
                NumberedList,
                Item,
                CodeBlock,
                ThematicBreak,
                HtmlBlock,
                Table,
                TableRow,
                TableCell {

    /// This block's children, in document order.
    ///
    /// Empty for the two leaves — a [ThematicBreak] has nothing inside it and a
    /// [CodeBlock]'s content is text rather than nodes.
    List<? extends MarkdownNode> children();
}
