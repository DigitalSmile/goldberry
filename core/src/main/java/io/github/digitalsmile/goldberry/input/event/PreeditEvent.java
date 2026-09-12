package io.github.digitalsmile.goldberry.input.event;

import java.util.Objects;

import io.github.digitalsmile.goldberry.widget.Element;

/// What an input method is in the middle of composing — `docs/gaps.md` G15.
///
/// The third of §7.1's keyboard events, and the one a Latin keyboard never
/// produces. [KeyEvent] is a key, [TextEvent] is text the platform has finished
/// translating, and this is the string **in between**: the underlined
/// composition a Japanese, Chinese or Korean user watches being assembled while
/// they choose among candidates.
///
/// ## It is not an edit
///
/// Nothing here has been accepted. The user may abandon the composition, and
/// they routinely do — typing `にほんご` and picking `日本語` replaces every
/// character of it. A field that inserted this into its value would be inserting
/// text the user has not chosen and then deleting it: flicker on screen, garbage
/// in the undo history, and a `Property` that fires for keystrokes that were
/// never meant to be characters.
///
/// So it is drawn **beside** the document rather than in it —
/// [io.github.digitalsmile.goldberry.text.edit.Editor] paints it at the caret,
/// underlined — and the value only changes when a [TextEvent] arrives
/// (ADR-0289).
///
/// ## Ending
///
/// An **empty** [#text()] means the composition is over. It arrives whether the
/// user accepted a candidate — in which case a [TextEvent] comes with it — or
/// abandoned one, in which case nothing does. A handler that only clears its
/// preedit on a [TextEvent] leaves a ghost on screen after `Escape`.
///
/// ## The offsets are chars, not bytes
///
/// The platform reports them in UTF-8 bytes. They are translated here, once, to
/// offsets into [#text()] as Java sees it — because every consumer is Java and
/// eleven of them getting it right is eleven chances to get it wrong. `-1` means
/// the platform did not report a selection, which several do not.
public final class PreeditEvent {

    private final String text;
    private final int start;
    private final int length;
    private final Element target;
    private boolean consumed;

    public PreeditEvent(String text, int start, int length, Element target) {
        this.text = Objects.requireNonNull(text, "text");
        this.start = start;
        this.length = length;
        this.target = target;
    }

    /// The composition so far, or `""` when it has ended.
    public String text() {
        return text;
    }

    /// Whether this event ends the composition rather than continuing it.
    public boolean isEnd() {
        return text.isEmpty();
    }

    /// Where the selection inside [#text()] starts, as a char offset, or `-1`
    /// when the platform reports none.
    public int start() {
        return start;
    }

    /// How many chars of [#text()] are selected, or `-1` when the platform
    /// reports none.
    public int length() {
        return length;
    }

    /// Where the caret sits inside the composition, as a char offset.
    ///
    /// The value a painter actually wants: an input method that reports a
    /// selection puts the caret at its end, and one that reports none has the
    /// caret at the end of what has been composed. Always within `[0, text
    /// length]`, so a painter need not check.
    public int caret() {
        if (start < 0) {
            return text.length();
        }
        var end = start + Math.max(0, length);
        return Math.clamp(end, 0, text.length());
    }

    public Element target() {
        return target;
    }

    public void consume() {
        consumed = true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    @Override
    public String toString() {
        return "preedit \"" + text + "\"@" + caret() + (consumed ? " consumed" : "");
    }
}
