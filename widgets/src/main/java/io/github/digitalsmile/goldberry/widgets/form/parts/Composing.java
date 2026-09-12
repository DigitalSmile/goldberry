package io.github.digitalsmile.goldberry.widgets.form.parts;

/// Where the composition an input method is assembling sits inside what a field
/// is drawing — `docs/gaps.md` G16.
///
/// **Display offsets, like everything else a field's parts are placed with.** A
/// `password` never composes (see below), so the mask is not in play here, but
/// the value a field hands its box is already in display space and this is one
/// more span in it.
///
/// ## Why a span rather than the string
///
/// Because the string is already in the display text. A field splices the
/// composition into what it draws — so the words after it move along, exactly as
/// [io.github.digitalsmile.goldberry.text.edit.Editor] does on a canvas
/// (ADR-0289) — and what is left to say is *which part of what you are drawing
/// is not text yet*. That is two spans: the whole composition, which is
/// underlined, and the clause the input method is currently converting, which is
/// highlighted.
///
/// ## The selection is not drawn while this is active
///
/// A composition replaces the selection when it commits, and every platform's
/// input method collapses the highlight when one starts. So a field's single
/// highlight is free, and it draws the **clause** instead — which is what a
/// clause is: a selection inside the composition's own little document.
///
/// @param start       where the composition begins, in display offsets
/// @param end         where it ends; equal to `start` when nothing is being composed
/// @param clauseStart where the converting clause begins, or -1 when the platform
///                    reports none — which several do not
/// @param clauseEnd   where it ends, or -1
public record Composing(int start, int end, int clauseStart, int clauseEnd) {

    /// Nothing is being composed, which is every field on a Latin keyboard and
    /// every field on any keyboard most of the time.
    public static final Composing NONE = new Composing(0, 0, -1, -1);

    /// A composition with no converting clause reported.
    public static Composing of(int start, int end) {
        return new Composing(start, end, -1, -1);
    }

    /// Whether an input method has a composition open over this field.
    public boolean isActive() {
        return end > start;
    }

    /// Whether the platform said which clause it is converting.
    public boolean hasClause() {
        return isActive() && clauseStart >= 0 && clauseEnd > clauseStart;
    }
}
