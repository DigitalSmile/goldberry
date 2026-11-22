package dev.goldberry.text.spell;

import java.util.Objects;

/// A word the checker did not know, as a range of one hard line.
///
/// @param start the first character, as an offset into the line
/// @param end   one past the last
/// @param word  the word as it is in the line
public record Misspelling(int start, int end, String word) {

    public Misspelling {
        Objects.requireNonNull(word, "word");
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException(
                    "a misspelling runs forward from offset 0 or later: " + start + ".." + end);
        }
        if (word.length() != end - start) {
            throw new IllegalArgumentException("\"" + word + "\" is not " + (end - start) + " characters long");
        }
    }

    /// Whether a caret at `offset` is in this word or just after it, where
    /// someone still typing it would be. Such a word is not flagged.
    public boolean holds(int offset) {
        return offset >= start && offset <= end;
    }

    /// Whether `offset` is on one of this word's characters, which is where a
    /// press opens the menu for it.
    public boolean covers(int offset) {
        return offset >= start && offset < end;
    }
}
