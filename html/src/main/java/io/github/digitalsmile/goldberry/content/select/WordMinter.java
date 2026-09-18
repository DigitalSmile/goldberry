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
///
/// ## A fold can skip ahead
///
/// [#mark()] and [#resume] are what let a fold hand back the widgets it built last
/// time instead of building them again — see [BlockMemo]. What a fold has to restore
/// in order to carry on turns out to be one number, and the reason it is one is
/// written on [Mark].
public final class WordMinter {

    private final WordGeometry geometry;

    /// How many blocks have been opened. The next one is this, so it is also the
    /// index of the block being minted into, plus one.
    private int opened;

    /// How many words have gone into the block being minted into.
    private int words;

    /// Where they go, or null before the first block is opened.
    private WordGeometry.@Nullable Block block;

    /// Whether the next word begins a block, and so is preceded by a newline.
    private boolean pendingBlock = true;

    public WordMinter(WordGeometry geometry) {
        this.geometry = geometry;
    }

    /// Where a fold has got to — what [#resume] needs in order to carry on as if it
    /// had walked what it skipped.
    ///
    /// **A block boundary and nothing else**, which is why it is one number. Between
    /// two blocks the words behind the minter are about to be forgotten and the next
    /// one starts a block, so the only thing that distinguishes one boundary from
    /// another is how many blocks are behind it. Carrying the word count as well —
    /// which the first version of this did — made a paragraph that gained a word move
    /// the mark of the paragraph after it, and cost a rebuild of the rest of the note
    /// for a number nobody would read again.
    ///
    /// @param opened how many blocks have been opened
    public record Mark(int opened) {}

    /// A new block starts here — a paragraph, a heading, a cell, a line of a fence.
    public void block() {
        pendingBlock = true;
    }

    /// Where this minter has got to, as the next block will see it.
    public Mark mark() {
        return new Mark(opened);
    }

    /// Whether the geometry still holds the blocks between here and `mark`.
    ///
    /// **Asked before anything is skipped**, because the answer is no whenever a
    /// build in between dropped them — and a fold that carried on anyway would hand
    /// back words reporting their rectangles into entries nothing reads.
    public boolean canResume(Mark mark) {
        return mark.opened() >= opened && geometry.keepBlocks(mark.opened());
    }

    /// Carries on from `mark`, as if this minter had minted what lies between.
    ///
    /// Leaves a block pending, because a mark is a boundary: whatever comes next is
    /// the first word of a block, which is what the fold was about to say anyway.
    public void resume(Mark mark) {
        opened = mark.opened();
        words = 0;
        pendingBlock = true;
        block = null;
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
        if (pendingBlock || block == null) {
            // **Before the word is registered, not after.** The word that opens a block
            // belongs to it, and counting afterwards put every block's first word in
            // the block above — which a triple-click showed by taking one paragraph and
            // the first word of the next.
            block = geometry.beginBlock(opened);
            opened++;
            words = 0;
            pendingBlock = false;
        }
        // The first word of the document is preceded by nothing, whatever it is part
        // of: a copy that opened with a newline is a copy nobody asked for.
        var first = opened == 1 && words == 0;
        var entry = geometry.word(block, words, text, first ? "" : prefix);
        words++;
        return new Word(text, attributes, entry, child);
    }
}
