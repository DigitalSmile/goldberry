package io.github.digitalsmile.goldberry.content.select;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;

/// Hands out the words of one document, in document order.
///
/// The fold's half of the selection machinery, and the only part of it either view
/// has to know about: instead of building a `text` widget per word, a fold asks this
/// for one and gets a [Word] that reports where it lands (ADR-0301).
///
/// It also carries the two things only the **fold** knows and the geometry cannot
/// work out afterwards:
///
/// - **Where the blocks are.** A wrapping row of words is a paragraph to a reader and
///   a flat list to everything else. [#block()] is the fold saying "a new one starts
///   here", which is what puts a newline in a copied selection and what a triple-click
///   takes.
/// - **Where the spaces went.** A paragraph's spaces are *gaps between boxes* rather
///   than characters, so a copy that joined the words back up would produce
///   `Thequickbrown`. Each word carries the separator that belongs in front of it.
///
/// One of these per build, like the fold that holds it.
public final class WordMinter {

    private final WordGeometry geometry;

    private int index;

    private int block = -1;

    /// Whether the next word begins a block, and so is preceded by a newline.
    private boolean pendingBlock = true;

    public WordMinter(WordGeometry geometry) {
        this.geometry = geometry;
    }

    /// A new block starts here — a paragraph, a heading, a cell, a line of a fence.
    public void block() {
        pendingBlock = true;
    }

    /// The next word, drawing `text`.
    ///
    /// @param startsToken whether a space belongs in front of it: true between two
    ///        words of a line, false between two pieces of one word — `*a*,` is an
    ///        emphasised piece and then a comma, and the comma is not a new word
    public Widget word(String text, Attributes attributes, boolean startsToken) {
        return mint(text, attributes, startsToken, null);
    }

    /// The next word, drawing `child` — a link's button, an image's picture.
    ///
    /// `text` is what it contributes to a copy: a link's label, and nothing at all for
    /// a picture, which is what a browser copies for an image.
    public Widget wrapping(Widget child, String text, Attributes attributes, boolean startsToken) {
        return mint(text, attributes, startsToken, child);
    }

    private Widget mint(String text, Attributes attributes, boolean startsToken, @Nullable Widget child) {
        var prefix = pendingBlock ? "\n" : startsToken ? " " : "";
        if (pendingBlock) {
            // **Before the word is registered, not after.** The word that opens a block
            // belongs to it, and counting afterwards put every block's first word in
            // the block above — which a triple-click showed by taking one paragraph and
            // the first word of the next.
            block++;
            pendingBlock = false;
        }
        // The first word of the document is preceded by nothing, whatever it is part
        // of: a copy that opened with a newline is a copy nobody asked for.
        var entry = geometry.word(index, text, index == 0 ? "" : prefix, Math.max(block, 0));
        var word = new Word(index, text, attributes, entry, child);
        index++;
        return word;
    }
}
