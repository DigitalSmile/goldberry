package io.github.digitalsmile.goldberry.widgets.form.parts;

import java.text.BreakIterator;

import io.github.digitalsmile.goldberry.text.edit.TextEdit;

/// What a `max-length` allows: how much room is left, and how much of an
/// insertion fits in it.
///
/// ## Why it is here and not in each control
///
/// `text-input` and `text-area` carried byte-identical copies of both answers,
/// down to the comment above the second one. Two implementations that must agree
/// and cannot see each other is one implementation with a comment where the
/// compiler should be — [io.github.digitalsmile.goldberry.widgets.form.Carets]'s
/// argument for the caret's width, and it applies harder to arithmetic than to a
/// number, because arithmetic has a bug in it and a number does not. The bug it
/// did have was in both copies and was fixed once, here.
///
/// In `…form.parts` for the reason the package exists: both controls can see it
/// and nothing outside the module can, so this is shared without becoming API.
/// Only what is genuinely the same is here — a `text-input` also asks its
/// `filter`, and that stays where the filter is.
public final class MaxLength {

    private MaxLength() {}

    /// How many more chars `edit` will take, or -1 when `maximum` is negative and
    /// there is no limit at all.
    ///
    /// What the selection would free up counts as room: typing over a full
    /// field's selection must work.
    public static int room(int maximum, TextEdit edit) {
        if (maximum < 0) {
            return -1;
        }
        return Math.max(0, maximum - edit.length() + (edit.end() - edit.start()));
    }

    /// `text` cut to at most `room` chars, never through a cluster.
    ///
    /// The cut is moved **back** to the nearest boundary a caret could sit at, so
    /// a paste that does not fit loses a whole character rather than half of one.
    /// The same `BreakIterator` [TextEdit] steps with, so the two cannot disagree
    /// about where a character ends.
    ///
    /// The previous attempt counted code points from the front and then took
    /// `Math.min` of the result and `room`, which put the answer back inside the
    /// pair it had just stepped over: `clip("a🎨b", 2)` ended in a lone high
    /// surrogate, which is not a character, shapes as `.notdef` and reaches the
    /// application as a broken string the moment the field reports its value.
    public static String clip(String text, int room) {
        if (room <= 0) {
            return "";
        }
        if (text.length() <= room) {
            return text;
        }
        var clusters = BreakIterator.getCharacterInstance();
        clusters.setText(text);
        var end = clusters.isBoundary(room) ? room : clusters.preceding(room);
        return end == BreakIterator.DONE || end <= 0 ? "" : text.substring(0, end);
    }
}
