package io.github.digitalsmile.goldberry.natives.md4c;

import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.md4c.enums.BlockType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.CellAlign;

/// What a block carries beside its type — md4c's `MD_BLOCK_*_DETAIL` structs, as
/// values.
///
/// **None of these is a struct layout, and that is the point.** md4c hands its
/// details to a callback as a `void*` into one of seven different structs; reading
/// them from Java would put all seven in the layout table, and a wrong offset in
/// one of them is a heading at level 218. The shim reads them instead — with the
/// compiler that built the library — and puts what it found into three integers of
/// an event record (ADR-0294). What arrives here has no layout to get wrong.
///
/// A block with nothing to say — a paragraph, a table row, the document — carries
/// [#NONE].
public sealed interface BlockDetail {

    /// The detail of a block that has none.
    BlockDetail NONE = new None();

    /// A block whose type is all there is to know: [BlockType#DOC], [BlockType#P],
    /// [BlockType#QUOTE], [BlockType#HR], [BlockType#HTML] and the three table
    /// grouping blocks.
    record None() implements BlockDetail {}

    /// `# heading`.
    ///
    /// @param level 1 to 6
    record Heading(int level) implements BlockDetail {}

    /// A bullet list.
    ///
    /// @param tight whether the items are tight — no blank line between them, which
    ///        CommonMark says means the items hold no paragraphs
    /// @param mark the bullet the author used: `-`, `+` or `*`
    record BulletList(boolean tight, char mark) implements BlockDetail {}

    /// A numbered list.
    ///
    /// @param start the first number, which is not always 1
    /// @param tight see [BulletList#tight()]
    /// @param delimiter `.` or `)`
    record NumberedList(int start, boolean tight, char delimiter) implements BlockDetail {}

    /// One item of either list.
    ///
    /// @param task whether this is a task-list item — `- [x] done` — which needs
    ///        [io.github.digitalsmile.goldberry.natives.md4c.enums.MarkdownFlag#TASK_LISTS]
    /// @param taskMark the character between the brackets: `x`, `X` or a space.
    ///        Meaningless unless [#task()]
    /// @param taskMarkOffset where that character is in the input, which is what an
    ///        editor needs in order to toggle it by writing one byte
    record Item(boolean task, char taskMark, int taskMarkOffset) implements BlockDetail {

        /// Whether this item is a task that is done.
        public boolean checked() {
            return task && (taskMark == 'x' || taskMark == 'X');
        }
    }

    /// A code block.
    ///
    /// @param info everything after the opening fence — `java {highlight=3}`
    /// @param language the first word of it, which is what a highlighter wants
    /// @param fence the fence character, or 0 for an indented block
    record Code(MarkdownAttribute info, MarkdownAttribute language, char fence) implements BlockDetail {

        public Code {
            Objects.requireNonNull(info, "info");
            Objects.requireNonNull(language, "language");
        }

        /// Whether the block was fenced rather than indented.
        public boolean fenced() {
            return fence != 0;
        }
    }

    /// A table.
    ///
    /// @param columns how many columns every row has
    /// @param headRows rows in the head — one, in every dialect md4c supports
    /// @param bodyRows rows in the body
    record Table(int columns, int headRows, int bodyRows) implements BlockDetail {}

    /// One cell of a table, head or body.
    ///
    /// @param align what the `:---:` row asked for
    record Cell(CellAlign align) implements BlockDetail {

        public Cell {
            Objects.requireNonNull(align, "align");
        }
    }
}
