package io.github.digitalsmile.goldberry.markdown;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.natives.md4c.BlockDetail;
import io.github.digitalsmile.goldberry.natives.md4c.MarkdownEvent;
import io.github.digitalsmile.goldberry.natives.md4c.Md4c;

/// Finding a task marker in the **source**, for
/// [Markdown#toggleTask(String, int)].
///
/// Package-private, and not a re-serialisation. The obvious implementation is `parse`,
/// edit the model, write it back out — and it is wrong for this job in a way worth
/// writing down: a re-serialisation returns *a* document with the same meaning rather
/// than **the author's file**, so it would silently reflow their tables, renumber
/// their lists and normalise their line endings, all because somebody ticked a box
/// (ADR-0300).
///
/// So this asks the parser where the box is and changes that one character.
/// Everything else in the file comes back byte for byte.
///
/// ## Why it asks md4c rather than matching a pattern
///
/// The two halves of ADR-0300 have to count the same tasks: the ordinal a pressed box
/// reports is md4c's, counted by walking the model, and the marker this rewrites has
/// to be the one that ordinal names. A scanner cannot agree with the parser about
/// that, because whether `- [ ]` on a line is a task box at all depends on what a
/// line does not carry — how deeply the list it belongs to is nested, whether it is
/// inside a fence or an indented code block, whether a block quote's `>` stands in
/// front of it. The version of this file that matched `^\s{0,3}` missed every task
/// indented four spaces under a parent item and ticked the next box instead, in a
/// list that looked entirely ordinary to whoever wrote it.
///
/// There is no pattern that gets that right, and the parser already knows: md4c
/// reports `task_mark_offset` for every item it made a task, which is where the
/// character between the brackets is in the input it was handed. That is one parse
/// per tick of one box, which is a keystroke's worth of work for an answer nothing
/// else can give.
///
/// The input md4c was handed is UTF-8, so the offset counts **bytes** — [#charAt] is
/// what turns it back into an index into the author's string.
final class Tasks {

    private Tasks() {}

    /// Where every task box's state character is in `markdown`, as UTF-8 byte offsets,
    /// in document order — which is the order the renderer numbers them in.
    ///
    /// [MarkdownSyntax#gitHub()] rather than the dialect the caller parsed with,
    /// because a task box *is* the GitHub dialect: read as plain CommonMark the same
    /// text holds no tasks at all, so there would be nothing to toggle and no ordinal
    /// to toggle it by.
    private static List<Integer> marks(String markdown) {
        if (markdown.isEmpty()) {
            return List.of();
        }
        var offsets = new ArrayList<Integer>();
        for (var event : Md4c.get().parse(markdown, MarkdownSyntax.gitHub().flags())) {
            if (event instanceof MarkdownEvent.EnterBlock(var _, BlockDetail.Item(var task, var _, var offset))
                    && task) {
                offsets.add(offset);
            }
        }
        return offsets;
    }

    /// The index in `markdown` of the character md4c reported at UTF-8 byte `offset`,
    /// or -1 when no character starts there.
    ///
    /// A document whose text is all ASCII walks this for nothing, and one with an
    /// emoji above its task list is the reason it walks it at all: the offset counts
    /// bytes and a `String` counts UTF-16 code units, so an unconverted offset would
    /// land to the right of the box by however much the characters above it cost.
    private static int charAt(String markdown, int offset) {
        var bytes = 0;
        var index = 0;
        while (index < markdown.length() && bytes < offset) {
            var codePoint = markdown.codePointAt(index);
            bytes += utf8Length(codePoint);
            index += Character.charCount(codePoint);
        }
        return bytes == offset && index < markdown.length() ? index : -1;
    }

    /// How many bytes `codePoint` took in the UTF-8 md4c was handed.
    ///
    /// An unpaired surrogate is one byte, because a `?` is what the encoder wrote for
    /// it: this has to count what the parser read rather than what the author meant.
    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (Character.isSurrogate((char) codePoint)) {
            return 1;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }

    /// `markdown` with the `index`th task box flipped, or unchanged when there is no
    /// such task.
    static String toggle(String markdown, int index) {
        var marks = marks(markdown);
        if (index >= marks.size()) {
            return markdown;
        }
        var at = charAt(markdown, marks.get(index));
        if (at < 0) {
            return markdown;
        }
        var flipped = markdown.charAt(at) == ' ' ? 'x' : ' ';
        return markdown.substring(0, at) + flipped + markdown.substring(at + 1);
    }

    /// How many task boxes `markdown` has.
    ///
    /// Only a test asks — and what it asks is now a tautology rather than the
    /// load-bearing question it used to be, since both counts come from the one parse.
    /// It stays because a test that walks the model and finds a different number would
    /// mean the fold and the event stream disagree, which is a real way to break this.
    static int count(String markdown) {
        return marks(markdown).size();
    }
}
