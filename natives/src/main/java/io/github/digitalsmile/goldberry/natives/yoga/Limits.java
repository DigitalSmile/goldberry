package io.github.digitalsmile.goldberry.natives.yoga;

import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;

/// How small and how large a box may be — CSS's `min-width`, `max-width`,
/// `min-height` and `max-height`.
///
/// One value rather than four fields on whatever holds it, for [Insets]'s reason
/// and one more. The [Insets] reason: the four are only meaningful together, and
/// a `Box` and a `ComputedStyle` would each have grown four components where they
/// now grow one. The extra one: they are the *same question* asked four ways, and
/// a caller that handled three of them is a caller with a bug nobody would find —
/// a dialog with a minimum width and no maximum reads as working right up until
/// somebody writes a long sentence in one.
///
/// Here beside [Insets] and [StyleLength], which is where the vocabulary `css`
/// and `layout` share already lives ([ADR-0172]).
///
/// ## Undefined, not zero
///
/// [StyleLength#UNDEFINED] on every edge is "no limit", and it has to be: a
/// minimum of zero is a real declaration that constrains nothing, but a *maximum*
/// of zero is a box that may not exist. Yoga spells "no limit" as `YGUndefined`,
/// which is what [StyleLength#UNDEFINED] reaches it as, so this is Yoga's own
/// default rather than a convention layered over it.
///
/// [StyleLength#AUTO] is not a value Yoga has for any of the four — it does not
/// export `YGNodeStyleSetMaxWidthAuto` — and [YogaNode] refuses it by name rather
/// than dropping it silently.
///
/// @param minWidth  the least it may be across, or [StyleLength#UNDEFINED]
/// @param maxWidth  the most, or undefined
/// @param minHeight the least it may be down, or undefined
/// @param maxHeight the most, or undefined
public record Limits(StyleLength minWidth, StyleLength maxWidth, StyleLength minHeight, StyleLength maxHeight) {

    /// No limit on any axis — what every box has until a stylesheet says
    /// otherwise, and Yoga's own default.
    public static final Limits NONE = new Limits(
            StyleLength.UNDEFINED, StyleLength.UNDEFINED,
            StyleLength.UNDEFINED, StyleLength.UNDEFINED);

    public Limits {
        Objects.requireNonNull(minWidth, "minWidth");
        Objects.requireNonNull(maxWidth, "maxWidth");
        Objects.requireNonNull(minHeight, "minHeight");
        Objects.requireNonNull(maxHeight, "maxHeight");
    }

    /// This, with a different `min-width`.
    public Limits minWidth(StyleLength value) {
        return new Limits(value, maxWidth, minHeight, maxHeight);
    }

    /// This, with a different `max-width`.
    public Limits maxWidth(StyleLength value) {
        return new Limits(minWidth, value, minHeight, maxHeight);
    }

    /// This, with a different `min-height`.
    public Limits minHeight(StyleLength value) {
        return new Limits(minWidth, maxWidth, value, maxHeight);
    }

    /// This, with a different `max-height`.
    public Limits maxHeight(StyleLength value) {
        return new Limits(minWidth, maxWidth, minHeight, value);
    }

    /// Whether any of the four says anything.
    ///
    /// Asked once per node per layout, so that a box with no limits — which is
    /// nearly every box — costs a field comparison rather than four foreign
    /// calls.
    public boolean isNone() {
        return minWidth == StyleLength.UNDEFINED
                && maxWidth == StyleLength.UNDEFINED
                && minHeight == StyleLength.UNDEFINED
                && maxHeight == StyleLength.UNDEFINED;
    }

    @Override
    public String toString() {
        return isNone()
                ? "Limits[none]"
                : "Limits[min " + minWidth + "×" + minHeight + ", max " + maxWidth + "×" + maxHeight + "]";
    }
}
