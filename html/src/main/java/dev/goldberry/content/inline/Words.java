package dev.goldberry.content.inline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.content.select.Word;
import dev.goldberry.content.select.WordMinter;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;

/// Collapses document whitespace and registers selectable words in source order.
/// Marks may change inside a word. Only source whitespace introduces a separator.
public record Words(String wordClass, WordMinter minter) {

    public static Words prefixed(String prefix, WordMinter minter) {
        return new Words(prefix + "-word", minter);
    }

    public sealed interface Piece permits Fragment, Node, Break {}

    /// An atomic fragment retains its internal whitespace, as in inline code.
    public record Fragment(
            String text,
            Set<String> marks,
            boolean atomic,
            @Nullable Locale language) implements Piece {
        public Fragment(String text, Set<String> marks, boolean atomic) {
            this(text, marks, atomic, null);
        }

        public static final Fragment SEPARATOR = new Fragment(" ", Set.of(), false);
    }

    /// A link or picture with the text it contributes to a copy.
    public record Node(Widget widget, String text, @Nullable Locale language) implements Piece {
        public Node(Widget widget, String text) {
            this(widget, text, null);
        }
    }

    public record Break() implements Piece {
        public static final Break HARD = new Break();
    }

    public List<Word> tokens(List<? extends Piece> pieces) {
        var words = new ArrayList<Word>();
        boolean separated = true;
        for (var piece : pieces) {
            switch (piece) {
                case Break _ -> separated = true;
                case Node(var widget, var label, var language) -> {
                    words.add(minter.wrapping(
                            widget,
                            label,
                            language == null ? Attributes.NONE : Attributes.NONE.lang(language),
                            separated));
                    separated = false;
                }
                case Fragment(var text, var marks, var atomic, var language)
                when atomic -> {
                    words.add(minter.word(text, attributes(marks, language), separated));
                    separated = false;
                }
                case Fragment(var text, var marks, var _, var language) -> {
                    var style = attributes(marks, language);
                    for (int start = 0; start < text.length(); ) {
                        if (Character.isWhitespace(text.charAt(start))) {
                            separated = true;
                            start++;
                            continue;
                        }
                        int end = start + 1;
                        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                            end++;
                        }
                        words.add(minter.word(text.substring(start, end), style, separated));
                        separated = false;
                        start = end;
                    }
                }
            }
        }
        return words;
    }

    /// Each hard break begins a new selection block. A blank line between
    /// breaks retains its height and its place in the copied document.
    public List<List<Word>> lines(List<? extends Piece> pieces) {
        var lines = new ArrayList<List<Word>>();
        int start = 0;
        for (int i = 0; i <= pieces.size(); i++) {
            if (i < pieces.size() && !(pieces.get(i) instanceof Break)) {
                continue;
            }
            if (start > 0) {
                minter.block();
            }
            var line = tokens(pieces.subList(start, i));
            boolean hardBreak = start > 0 || i < pieces.size();
            lines.add(line.isEmpty() && hardBreak ? List.of(whole(" ", Set.of())) : line);
            start = i + 1;
        }
        return lines;
    }

    public Word whole(String text, Set<String> marks) {
        return minter.word(text, attributes(marks, null), true);
    }

    /// The word class, plus whatever marks the piece is inside.
    ///
    /// The word class first and always: it is the hook an application restyles every
    /// word through, and a word with no marks still has it.
    private Attributes attributes(Set<String> marks, @Nullable Locale language) {
        var classes = new LinkedHashSet<String>(marks.size() + 1);
        classes.add(wordClass);
        classes.addAll(marks);
        var attributes = Attributes.NONE.classes(classes.toArray(String[]::new));
        return language == null ? attributes : attributes.lang(language);
    }

    /// `marks` with `mark` added, without touching the caller's set.
    ///
    /// The mark stack is passed down the inline tree rather than mutated, so that a
    /// bold word inside emphasis carries both classes and the word after the emphasis
    /// carries neither.
    public static Set<String> and(Set<String> marks, String mark) {
        var all = new ArrayList<String>(marks.size() + 1);
        all.addAll(marks);
        all.add(mark);
        return Set.copyOf(all);
    }
}
