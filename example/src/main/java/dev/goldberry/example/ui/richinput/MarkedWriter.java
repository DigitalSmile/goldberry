package dev.goldberry.example.ui.richinput;

import java.util.Objects;

import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedText;

/// Writes a [MarkedText] a stretch at a time, so a sample says which words are
/// bold rather than which offsets are.
///
/// ```java
/// MarkedWriter.start().plain("Ship it ").with("today", MarkKind.Style.BOLD).text();
/// ```
public final class MarkedWriter {

    private MarkedText text = MarkedText.EMPTY;

    private MarkedWriter() {}

    /// An empty text to write into.
    public static MarkedWriter start() {
        return new MarkedWriter();
    }

    /// `words` with no marks.
    public MarkedWriter plain(String words) {
        return with(words);
    }

    /// `words` under every kind of `kinds`.
    public MarkedWriter with(String words, MarkKind... kinds) {
        Objects.requireNonNull(words, "words");
        var start = text.length();
        var next = MarkedText.of(text.text() + words);
        for (var mark : text.marks()) {
            next = next.mark(mark.start(), mark.end(), mark.kind());
        }
        for (var kind : kinds) {
            next = next.mark(start, start + words.length(), kind);
        }
        text = next;
        return this;
    }

    /// What has been written.
    public MarkedText text() {
        return text;
    }
}
