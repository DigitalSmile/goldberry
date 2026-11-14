package dev.goldberry.text.mark;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.text.edit.TextEdit;

/// A [MarkedText] being edited: the caret, the selection, and what the next
/// typed letter will look like.
///
/// ```java
/// var edit = MarkedEdit.of(MarkedText.of("Ship it"));
/// edit = edit.toggle(MarkKind.Style.BOLD);   // nothing selected: the next letters are bold
/// edit = edit.insert("!");                   // "Ship it!", the "!" bold
/// edit = edit.wordLeft(true).toggle(MarkKind.Style.ITALIC);
/// ```
///
/// [TextEdit] with marks, and every movement and deletion is `TextEdit`'s: the
/// caret steps by graphemes and words the same way. What marks add is in three
/// places:
///
/// - **Typing** takes [MarkedText#kindsInheritedAt] at the caret, or the kinds
///   [#typing()] holds once a toggle was pressed with nothing selected. Any
///   movement forgets them, as every editor does.
/// - **Atoms** are stepped over. A movement that would stop inside one stops at
///   the end it was heading for, a click stops at the nearer end, a selection
///   grows to cover it, and a deletion that touches it removes all of it.
/// - **A paste** keeps its own marks and takes nothing from its neighbours.
///
/// A value: every operation returns a new one, so an undo stack holds these
/// and formatting comes back with the text.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
///
/// @param value  the text and its marks
/// @param anchor where the selection started
/// @param caret  where the caret is
/// @param typing the kinds the next typed letter takes, or null to take what
///               the text around the caret gives. Never an atomic kind
public record MarkedEdit(
        MarkedText value, int anchor, int caret, @Nullable Set<MarkKind> typing) {

    /// No text.
    public static final MarkedEdit EMPTY = new MarkedEdit(MarkedText.EMPTY, 0, 0, null);

    public MarkedEdit {
        Objects.requireNonNull(value, "value");
        anchor = Math.clamp(anchor, 0, value.length());
        caret = Math.clamp(caret, 0, value.length());
        if (typing != null) {
            typing = Set.copyOf(typing);
            for (var kind : typing) {
                if (kind.isAtomic()) {
                    throw new IllegalArgumentException("nothing typed becomes part of an atom, so " + kind
                            + " cannot be what the next letter takes");
                }
            }
        }
    }

    /// `value` with the caret at its end.
    public static MarkedEdit of(MarkedText value) {
        return new MarkedEdit(value, value.length(), value.length(), null);
    }

    /// `value` with the caret at its start.
    public static MarkedEdit atStart(MarkedText value) {
        return new MarkedEdit(value, 0, 0, null);
    }

    /// The plain text.
    public String text() {
        return value.text();
    }

    /// The same text, caret and selection without the marks: what the plain
    /// editing rules and the geometry read.
    public TextEdit plain() {
        return new TextEdit(value.text(), anchor, caret);
    }

    /// The lower of the two offsets.
    public int start() {
        return Math.min(anchor, caret);
    }

    /// The higher of the two offsets.
    public int end() {
        return Math.max(anchor, caret);
    }

    /// Whether anything is selected.
    public boolean hasSelection() {
        return anchor != caret;
    }

    /// The selected text, or `""`.
    public String selectedText() {
        return value.text().substring(start(), end());
    }

    /// The selection, with its marks: what a copy puts on the clipboard.
    public MarkedText selected() {
        return value.slice(start(), end());
    }

    /// The kinds that are on where the caret is: over all of the selection
    /// when there is one, and otherwise what the next typed letter takes. What a
    /// formatting bar shows as pressed.
    public Set<MarkKind> activeKinds() {
        if (hasSelection()) {
            return value.kindsOver(start(), end());
        }
        return typingKinds();
    }

    // --- moving ---------------------------------------------------------------

    /// The caret at `offset`. A click inside an atom lands at its nearer end;
    /// a selection extended into one grows over it.
    public MarkedEdit caretTo(int offset, boolean extend) {
        var moved = plain().caretTo(offset, extend);
        var toward = extend ? Integer.signum(moved.caret() - anchor) : 0;
        return moved(moved, toward);
    }

    /// One position left. `Left`.
    public MarkedEdit left(boolean extend) {
        return moved(plain().left(extend), -1);
    }

    /// One position right. `Right`.
    public MarkedEdit right(boolean extend) {
        return moved(plain().right(extend), 1);
    }

    /// To the start of the word to the left. `Ctrl+Left`.
    public MarkedEdit wordLeft(boolean extend) {
        return moved(plain().wordLeft(extend), -1);
    }

    /// Past the end of the word to the right. `Ctrl+Right`.
    public MarkedEdit wordRight(boolean extend) {
        return moved(plain().wordRight(extend), 1);
    }

    /// To the start of the text.
    public MarkedEdit toStart(boolean extend) {
        return moved(plain().toStart(extend), -1);
    }

    /// To the end of the text.
    public MarkedEdit toEnd(boolean extend) {
        return moved(plain().toEnd(extend), 1);
    }

    /// To the start of the hard line. `Home`.
    public MarkedEdit toLineStart(boolean extend) {
        return moved(plain().toLineStart(extend), -1);
    }

    /// To the end of the hard line. `End`.
    public MarkedEdit toLineEnd(boolean extend) {
        return moved(plain().toLineEnd(extend), 1);
    }

    /// Everything selected.
    public MarkedEdit selectAll() {
        return new MarkedEdit(value, 0, value.length(), null);
    }

    /// The selection dropped, the caret kept.
    public MarkedEdit collapse() {
        return new MarkedEdit(value, caret, caret, null);
    }

    /// The word at `offset` selected, grown to cover an atom it reaches into. A
    /// double-click.
    public MarkedEdit wordAt(int offset) {
        var word = plain().wordAt(offset);
        var span = value.atomicRange(word.start(), word.end());
        return new MarkedEdit(value, span.start(), span.end(), null);
    }

    /// `plain`'s caret and anchor, moved out of any atom: toward its end when
    /// `toward` is positive, its start when negative, the nearer end when 0.
    private MarkedEdit moved(TextEdit plain, int toward) {
        var nextCaret = outOfAtom(plain.caret(), toward);
        var nextAnchor = plain.anchor() == plain.caret() ? nextCaret : outOfAtom(plain.anchor(), -toward);
        return new MarkedEdit(value, nextAnchor, nextCaret, null);
    }

    private int outOfAtom(int offset, int toward) {
        var atom = value.atomAt(offset);
        if (atom.isEmpty()) {
            return offset;
        }
        var mark = atom.get();
        if (toward > 0) {
            return mark.end();
        }
        if (toward < 0) {
            return mark.start();
        }
        return offset - mark.start() <= mark.end() - offset ? mark.start() : mark.end();
    }

    // --- editing --------------------------------------------------------------

    /// `typed` in place of the selection, with the caret after it. Typing, and
    /// an input method's commit.
    ///
    /// It takes [#typing()] if a toggle set it, and otherwise what the text
    /// gives: the kinds of the first selected char when replacing a selection,
    /// [MarkedText#kindsInheritedAt] the caret when not.
    public MarkedEdit insert(String typed) {
        Objects.requireNonNull(typed, "typed");
        if (typed.isEmpty() && !hasSelection()) {
            return this;
        }
        return replaced(start(), end(), MarkedText.of(typed), typingKinds());
    }

    /// `pasted` in place of the selection, its own marks kept and nothing taken
    /// from around it. A paste.
    public MarkedEdit insert(MarkedText pasted) {
        Objects.requireNonNull(pasted, "pasted");
        if (pasted.isEmpty() && !hasSelection()) {
            return this;
        }
        return replaced(start(), end(), pasted, Set.of());
    }

    /// `[start, end)` replaced by `with`, the caret after it and nothing
    /// selected: how an application puts a mention in place of the query typed
    /// for it.
    public MarkedEdit replace(int start, int end, MarkedText with) {
        Objects.requireNonNull(with, "with");
        return replaced(start, end, with, Set.of());
    }

    /// The selection, or one position back, removed. `Backspace`.
    public MarkedEdit backspace() {
        return deleted(plain().backspace());
    }

    /// The selection, or one position forward, removed. `Delete`.
    public MarkedEdit delete() {
        return deleted(plain().delete());
    }

    /// The word before the caret removed. `Ctrl+Backspace`.
    public MarkedEdit deleteWordBefore() {
        return deleted(plain().deleteWordBefore());
    }

    /// The word after the caret removed. `Ctrl+Delete`.
    public MarkedEdit deleteWordAfter() {
        return deleted(plain().deleteWordAfter());
    }

    /// `kind` turned on over the selection, or off when all of it has it
    /// already. With nothing selected, it turns `kind` on or off for what is
    /// typed next.
    ///
    /// Turning a kind on for typing turns off the kinds it conflicts with: code
    /// is typed without bold.
    ///
    /// @throws IllegalArgumentException for an atomic kind: an atom is
    ///         inserted with [#replace], not toggled
    public MarkedEdit toggle(MarkKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (kind.isAtomic()) {
            throw new IllegalArgumentException(
                    "an atom is put in with replace(), over text of its own; " + kind + " cannot be toggled");
        }
        if (hasSelection()) {
            var next = value.has(start(), end(), kind)
                    ? value.unmark(start(), end(), kind)
                    : value.mark(start(), end(), kind);
            return new MarkedEdit(next, anchor, caret, null);
        }
        var kinds = new LinkedHashSet<>(typingKinds());
        if (!kinds.remove(kind)) {
            kinds.removeIf(other -> MarkKind.conflict(other, kind));
            kinds.add(kind);
        }
        return new MarkedEdit(value, anchor, caret, kinds);
    }

    /// `kind` over the selection whatever it had before, such as a link with a
    /// new address. Nothing selected changes nothing.
    public MarkedEdit mark(MarkKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (!hasSelection()) {
            return this;
        }
        return new MarkedEdit(value.mark(start(), end(), kind), anchor, caret, null);
    }

    /// `kind` off the selection. Nothing selected changes nothing.
    public MarkedEdit unmark(MarkKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (!hasSelection()) {
            return this;
        }
        return new MarkedEdit(value.unmark(start(), end(), kind), anchor, caret, null);
    }

    /// Every kind but the atoms off the selection; with nothing selected, plain
    /// typing from here.
    public MarkedEdit clearFormatting() {
        if (!hasSelection()) {
            return new MarkedEdit(value, anchor, caret, Set.of());
        }
        return new MarkedEdit(value.clearFormatting(start(), end()), anchor, caret, null);
    }

    /// `replacement` as the whole value, the caret kept where it fits. What a
    /// bound value changing under the editor does.
    public MarkedEdit withValue(MarkedText replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (replacement.equals(value)) {
            return this;
        }
        var plain = plain().withText(replacement.text());
        var next = new MarkedEdit(replacement, plain.anchor(), plain.caret(), null);
        return next.moved(next.plain(), 0);
    }

    /// What the next typed letter takes: [#typing()] when a toggle set it, and
    /// otherwise what the text gives at the caret, or at the start of the
    /// selection being replaced.
    public Set<MarkKind> typingKinds() {
        if (typing != null) {
            return typing;
        }
        if (hasSelection()) {
            var kinds = new LinkedHashSet<>(value.kindsAt(start()));
            kinds.removeIf(MarkKind::isAtomic);
            return Set.copyOf(kinds);
        }
        return value.kindsInheritedAt(caret);
    }

    private MarkedEdit replaced(int start, int end, MarkedText with, Set<MarkKind> inherit) {
        var span = value.atomicRange(start, end);
        var next = value.replace(span.start(), span.end(), with, inherit);
        var at = span.start() + with.length();
        return new MarkedEdit(next, at, at, null);
    }

    /// The deletion `after` made to the plain text, made to the marked one:
    /// the removed chars run from `after`'s caret for as many chars as the text
    /// lost, widened over any atom they touch.
    private MarkedEdit deleted(TextEdit after) {
        var removed = value.length() - after.text().length();
        if (removed == 0) {
            return after.caret() == caret && after.anchor() == anchor ? this : moved(after, 0);
        }
        return replaced(after.caret(), after.caret() + removed, MarkedText.EMPTY, Set.of());
    }

    @Override
    public String toString() {
        var marker = hasSelection() ? start() + ".." + end() : String.valueOf(caret);
        return "MarkedEdit[" + value.length() + " chars, " + value.marks().size() + " marks, caret " + marker
                + (typing == null ? "" : ", typing " + typing) + "]";
    }
}
