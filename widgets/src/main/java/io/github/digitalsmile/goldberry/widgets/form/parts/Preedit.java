package io.github.digitalsmile.goldberry.widgets.form.parts;

/// What an input method is composing over a field, held beside the field's text
/// and never in it — `docs/gaps.md` G16.
///
/// A composition is a proposal: `にほんご` becomes `日本語` and every character of
/// what was typed is replaced when the user picks a candidate. A control that
/// inserted this would report keystrokes the user never chose and fill the undo
/// history with them (ADR-0292). So it lives here, is spliced into what is
/// *drawn* through [#composingAt], and the value only moves when the accepted
/// candidate arrives as committed text.
///
/// ## Why it is a part and not four fields in each control
///
/// `text-input` and `text-area` had the same four fields and the same three
/// operations on them, written twice. The copies drifted in the way copies do —
/// each cleared three of the four fields when the control lost focus — and they
/// carried the same bug, which is the one below.
///
/// ## `clauseEnd` was a length wearing an end's name
///
/// The platform reports a clause as **a start and a length**: that is
/// [io.github.digitalsmile.goldberry.input.event.PreeditEvent#length()], that is
/// SDL's `SDL_TextEditingEvent`, and that is what all three call sites pass. Both
/// controls' `compose` read it as a length and both interfaces documented it as
/// an end, so the name said one thing and every caller did another. The length is
/// the truth — a doc comment cannot change what SDL puts in the struct — and the
/// argument is named `clauseLength` everywhere now. An **end** is what a painter
/// wants, so the conversion happens here, once, and [Composing] still carries
/// ends.
///
/// The cost of the disagreement was a missed update: [#wouldChange] compared the
/// text, the caret and the clause's start, and not its extent. An input method
/// that grows or shrinks the clause it is converting without moving its start —
/// which is what `Shift+Right` does in every Japanese IME while a candidate list
/// is open — changed nothing the field drew, so the highlight stayed over the
/// clause before last.
public final class Preedit {

    private String text = "";
    private int caret;
    private int clauseStart = -1;
    private int clauseEnd = -1;

    /// Whether nothing is being composed, which is every field on a Latin
    /// keyboard and every field on any keyboard most of the time.
    public boolean isEmpty() {
        return text.isEmpty();
    }

    /// The composition so far, or `""`.
    public String text() {
        return text;
    }

    /// Where the caret sits inside [#text()], as a char offset.
    public int caret() {
        return caret;
    }

    /// Whether [#set] with these four would draw anything different.
    ///
    /// Every field of the composition, because every one of them is on screen:
    /// the string is drawn, the caret is drawn inside it, and the clause is
    /// highlighted. A comparison that leaves one out is a change the field will
    /// not repaint for.
    public boolean wouldChange(String composition, int at, int start, int length) {
        return !text.equals(composition)
                || caret != clampedCaret(composition, at)
                || clauseStart != clampedStart(composition, start)
                || clauseEnd != clauseEnd(composition, start, length);
    }

    /// Takes what the platform reported, clamped to the composition it came with.
    ///
    /// @param composition what is being composed, or `""` when it has ended
    /// @param at          where the caret sits inside it, as a char offset
    /// @param start       where the converting clause begins, or -1 for none
    /// @param length      how many chars of it are in that clause — a **length**,
    ///                    see the class note
    public void set(String composition, int at, int start, int length) {
        text = composition;
        caret = clampedCaret(composition, at);
        clauseStart = clampedStart(composition, start);
        clauseEnd = clauseEnd(composition, start, length);
    }

    /// Drops the composition, all four fields of it.
    public void clear() {
        text = "";
        caret = 0;
        clauseStart = -1;
        clauseEnd = -1;
    }

    /// Where this composition sits once it has been spliced into the display
    /// string at `offset` — the two spans a field draws.
    public Composing composingAt(int offset) {
        return new Composing(
                offset,
                offset + text.length(),
                clauseStart < 0 ? -1 : offset + clauseStart,
                clauseEnd < 0 ? -1 : offset + clauseEnd);
    }

    private static int clampedCaret(String composition, int at) {
        return Math.clamp(at, 0, composition.length());
    }

    private static int clampedStart(String composition, int start) {
        return start < 0 ? -1 : Math.clamp(start, 0, composition.length());
    }

    private static int clauseEnd(String composition, int start, int length) {
        return start < 0 ? -1 : Math.clamp(start + Math.max(0, length), 0, composition.length());
    }
}
