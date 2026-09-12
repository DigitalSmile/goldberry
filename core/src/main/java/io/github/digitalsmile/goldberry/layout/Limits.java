package io.github.digitalsmile.goldberry.layout;

import java.util.Objects;

/// How small and how large a box may be — CSS's `min-width`, `max-width`,
/// `min-height` and `max-height`.
///
/// One value rather than four fields on whatever holds it, for [Insets]'s reason
/// and one more. The [Insets] reason: the four are only meaningful together, and
/// a `Box` and a `ComputedStyle` would each have grown four components where they
/// now grow one. The extra one: they are the *same question* asked four ways, and
/// a caller that handled three of them is a caller with a bug nobody would find —
/// a dialog with a minimum width and no maximum reads as working right up until
/// somebody writes a long sentence in one (ADR-0181).
///
/// ## Undefined, not zero
///
/// [Length#UNDEFINED] on every edge is "no limit", and it has to be: a minimum of
/// zero is a real declaration that constrains nothing, but a *maximum* of zero is
/// a box that may not exist.
///
/// [Length#AUTO] is not a value any of the four accepts, and the layout boundary
/// refuses it by name rather than dropping it silently.
///
/// @param minWidth  the least it may be across, or [Length#UNDEFINED]
/// @param maxWidth  the most, or undefined
/// @param minHeight the least it may be down, or undefined
/// @param maxHeight the most, or undefined
public record Limits(Length minWidth, Length maxWidth, Length minHeight, Length maxHeight) {

    /// No limit on any axis — what every box has until a stylesheet says
    /// otherwise, and the layout engine's own default.
    public static final Limits NONE =
            new Limits(Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED, Length.UNDEFINED);

    public Limits {
        Objects.requireNonNull(minWidth, "minWidth");
        Objects.requireNonNull(maxWidth, "maxWidth");
        Objects.requireNonNull(minHeight, "minHeight");
        Objects.requireNonNull(maxHeight, "maxHeight");
    }

    /// This, with a different `min-width`.
    public Limits minWidth(Length value) {
        return new Limits(value, maxWidth, minHeight, maxHeight);
    }

    /// This, with a different `max-width`.
    public Limits maxWidth(Length value) {
        return new Limits(minWidth, value, minHeight, maxHeight);
    }

    /// This, with a different `min-height`.
    public Limits minHeight(Length value) {
        return new Limits(minWidth, maxWidth, value, maxHeight);
    }

    /// This, with a different `max-height`.
    public Limits maxHeight(Length value) {
        return new Limits(minWidth, maxWidth, minHeight, value);
    }

    /// Whether any of the four says anything.
    ///
    /// Asked once per node per layout, so that a box with no limits — which is
    /// nearly every box — costs a field comparison rather than four foreign
    /// calls.
    public boolean isNone() {
        return minWidth == Length.UNDEFINED
                && maxWidth == Length.UNDEFINED
                && minHeight == Length.UNDEFINED
                && maxHeight == Length.UNDEFINED;
    }

    @Override
    public String toString() {
        return isNone()
                ? "Limits[none]"
                : "Limits[min " + minWidth + "×" + minHeight + ", max " + maxWidth + "×" + maxHeight + "]";
    }
}
