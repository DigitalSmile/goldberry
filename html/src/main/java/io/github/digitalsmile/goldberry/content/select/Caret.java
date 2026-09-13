package io.github.digitalsmile.goldberry.content.select;

/// A place in a rendered document: a word, and a character in it.
///
/// The unit a selection is made of, and it is deliberately **not** an offset into
/// the source. A rendered document is a tree of words whose relationship to the text
/// that produced it is the parser's business, and a selection that had to be
/// expressed in source offsets would need every fold to carry them (ADR-0301). What
/// a reader selects is what they can see.
///
/// Comparable in document order, because "which end of the drag came first" is the
/// only question a selection asks of two of these.
///
/// @param word the word's position in document order, or -1 for nowhere
/// @param offset how many characters into that word, from zero
record Caret(int word, int offset) implements Comparable<Caret> {

    /// No place at all — what a pointer over an empty document reports, and what a
    /// cleared selection holds.
    static final Caret NONE = new Caret(-1, 0);

    boolean isNone() {
        return word < 0;
    }

    @Override
    public int compareTo(Caret other) {
        return word != other.word ? Integer.compare(word, other.word) : Integer.compare(offset, other.offset);
    }

    Caret min(Caret other) {
        return compareTo(other) <= 0 ? this : other;
    }

    Caret max(Caret other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
