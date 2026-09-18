package io.github.digitalsmile.goldberry.markdown.view;

import java.util.Locale;

/// A note of about a given size, and a cursor in the middle of it.
///
/// The document [MarkdownFrameBenchmark] types into. It is
/// deliberately not one long paragraph: a real note is headings, prose, bullets,
/// fences and quotations, and a benchmark over a single block would measure the
/// one case where reuse has nothing to reuse.
///
/// The cursor sits **inside a word in the middle**, which is the interesting
/// place: everything above it is unchanged and everything below it has moved by
/// nothing at all, so what a rebuild costs is a question about matching rather
/// than about parsing.
final class Notes {

    /// One section of the pattern below, about 400 bytes.
    private static final String SECTION = """
            ## Section %1$d

            Prose that wraps, because a paragraph that fits on one line is not the
            paragraph a preview spends its frame on. Section %1$d says the same
            thing as the one before it and the one after it, which is what makes
            the note a size rather than a document.

            - A bullet in section %1$d
            - A second bullet, with *emphasis* and `code()` in it
            - A third, [pointing somewhere](http://example.com/%1$d)

            > A quotation, held in section %1$d.

            ```java
            var section = %1$d;
            ```

            """;

    private final String prefix;

    private final String suffix;

    private final String text;

    private Notes(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.text = prefix + suffix;
    }

    /// A note of roughly `bytes` characters.
    static Notes of(int bytes) {
        var note = new StringBuilder();
        for (var section = 1; note.length() < bytes; section++) {
            note.append(SECTION.formatted(section));
        }
        var cursor = cursorIn(note);
        return new Notes(note.substring(0, cursor), note.substring(cursor));
    }

    /// Where a reader is typing: inside a word, about halfway down.
    ///
    /// Inside a word rather than between two, because that is the keystroke that
    /// leaves the word count alone — and a fold that numbers its words by their
    /// position in the document can only reuse what has not been renumbered.
    private static int cursorIn(CharSequence note) {
        var cursor = note.length() / 2;
        while (cursor + 1 < note.length()
                && !(Character.isLetter(note.charAt(cursor)) && Character.isLetter(note.charAt(cursor + 1)))) {
            cursor++;
        }
        return cursor + 1;
    }

    /// The note as it was opened.
    String text() {
        return text;
    }

    /// The note after `keystrokes` letters have been typed into that word.
    ///
    /// A different document every time and the **same word count** every time,
    /// which is the ordinary keystroke: a letter added to a word nobody else can
    /// see the length of.
    String typed(int keystrokes) {
        return prefix + "abcdefgh".charAt(keystrokes % 8) + suffix;
    }

    /// The same, with a **space** arriving and leaving every other keystroke — so
    /// the word count of the note changes from one document to the next and every
    /// word below the cursor is renumbered.
    ///
    /// The case a positional fold cannot reuse past, and the reason this exists is
    /// to put a number on that rather than to leave it as a caveat.
    String split(int keystrokes) {
        return prefix + (keystrokes % 2 == 0 ? " " : "") + "abcdefgh".charAt(keystrokes % 8) + suffix;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "a %.1f kB note, cursor at %d", text.length() / 1000.0, prefix.length());
    }
}
