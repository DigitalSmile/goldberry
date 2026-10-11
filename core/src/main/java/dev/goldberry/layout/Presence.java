package dev.goldberry.layout;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.compositing.Compositing;
import dev.goldberry.css.container.Containment;
import dev.goldberry.text.bidi.UnicodeBidi;

/// Whether a box is there, the shape it keeps, where it is placed and drawn
/// among its siblings, which way it runs, and whether it is seen, hit and
/// selected: `display`, `aspect-ratio`, `z-index`, `order`, `direction`,
/// `unicode-bidi`, `visibility`, `pointer-events` and `user-select`.
///
/// One record for the properties that decide whether and how a box takes part,
/// so that the computed style and the box carry them as one component.
///
/// `direction` and the last three **inherit**, as CSS has them, and are per box
/// rather than per subtree: a `visible` child under a `hidden` parent is drawn,
/// and a child that says `pointer-events: auto` under one that says `none` is
/// hit.
///
/// @param display       whether the node has a box, its children's, or none
/// @param aspectRatio   width over height, or NaN for `auto`. Finite and above
///                      zero otherwise
/// @param visibility    whether the box is drawn; a hidden one keeps its room
/// @param pointerEvents whether the pointer finds the box, or what is under it
/// @param userSelect    whether its text may be selected
/// @param zIndex        where it is drawn among its siblings: higher is later,
///                      equal keeps document order; 0 is `auto`
/// @param order         where it is laid out and drawn among its siblings:
///                      lower is first, equal keeps document order
/// @param direction     which way its inline axis runs
/// @param unicodeBidi   where its text's base direction comes from
/// @param compositing   how it is drawn over what is under it: `filter`,
///                      `clip-path`, `mix-blend-mode` and `isolation`
/// @param boxSizing     what its `width` and `height` measure
/// @param containment   what it is as a query container
public record Presence(
        Display display,
        double aspectRatio,
        Visibility visibility,
        PointerEvents pointerEvents,
        UserSelect userSelect,
        int zIndex,
        int order,
        Direction direction,
        UnicodeBidi unicodeBidi,
        Compositing compositing,
        BoxSizing boxSizing,
        Containment containment) {

    /// A visible flex box with no ratio of its own, hit by the pointer and
    /// selectable as its content allows, left to right: what every node is
    /// unless a rule says otherwise.
    public static final Presence NORMAL = new Presence(
            Display.FLEX,
            Double.NaN,
            Visibility.VISIBLE,
            PointerEvents.AUTO,
            UserSelect.AUTO,
            0,
            0,
            Direction.LTR,
            UnicodeBidi.NORMAL,
            Compositing.NONE,
            BoxSizing.BORDER_BOX,
            Containment.NONE);

    public Presence {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(unicodeBidi, "unicodeBidi");
        Objects.requireNonNull(compositing, "compositing");
        Objects.requireNonNull(boxSizing, "boxSizing");
        Objects.requireNonNull(containment, "containment");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(pointerEvents, "pointerEvents");
        Objects.requireNonNull(userSelect, "userSelect");
        if (!Double.isNaN(aspectRatio) && !(aspectRatio > 0 && Double.isFinite(aspectRatio))) {
            throw new IllegalArgumentException(
                    "an aspect ratio is above zero and finite, or NaN for auto, not " + aspectRatio);
        }
    }

    /// A display and a ratio, seen, hit and selectable, in document order.
    public Presence(Display display, double aspectRatio) {
        this(display, aspectRatio, Visibility.VISIBLE, PointerEvents.AUTO, UserSelect.AUTO, 0);
    }

    /// The same, in document order.
    public Presence(
            Display display,
            double aspectRatio,
            Visibility visibility,
            PointerEvents pointerEvents,
            UserSelect userSelect) {
        this(display, aspectRatio, visibility, pointerEvents, userSelect, 0);
    }

    /// The same, drawn at `zIndex`, laid out in document order, left to right.
    public Presence(
            Display display,
            double aspectRatio,
            Visibility visibility,
            PointerEvents pointerEvents,
            UserSelect userSelect,
            int zIndex) {
        this(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                0,
                Direction.LTR,
                UnicodeBidi.NORMAL,
                Compositing.NONE,
                BoxSizing.BORDER_BOX,
                Containment.NONE);
    }

    /// This presence with `display` replaced.
    public Presence display(Display value) {
        return new Presence(
                value,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                order,
                direction,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `aspect-ratio` replaced: NaN for `auto`.
    public Presence aspectRatio(double value) {
        return new Presence(
                display,
                value,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                order,
                direction,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `visibility` replaced.
    public Presence visibility(Visibility value) {
        return new Presence(
                display,
                aspectRatio,
                value,
                pointerEvents,
                userSelect,
                zIndex,
                order,
                direction,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `pointer-events` replaced.
    public Presence pointerEvents(PointerEvents value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                value,
                userSelect,
                zIndex,
                order,
                direction,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `user-select` replaced.
    public Presence userSelect(UserSelect value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                value,
                zIndex,
                order,
                direction,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `z-index` replaced; 0 is `auto`.
    public Presence zIndex(int value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                value,
                order,
                direction,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `order` replaced.
    public Presence order(int value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                value,
                direction,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `direction` replaced.
    public Presence direction(Direction value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                order,
                value,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `unicode-bidi` replaced.
    public Presence unicodeBidi(UnicodeBidi value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                order,
                direction,
                value,
                compositing,
                boxSizing,
                containment);
    }

    /// The inherited four taken from `parent`, the rest kept.
    public Presence inheritingFrom(Presence parent) {
        return new Presence(
                display,
                aspectRatio,
                parent.visibility,
                parent.pointerEvents,
                parent.userSelect,
                zIndex,
                order,
                parent.direction,
                unicodeBidi,
                compositing,
                boxSizing,
                containment);
    }

    /// This presence with `clip-path`, `mix-blend-mode` and `isolation`
    /// replaced.
    public Presence compositing(Compositing value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                order,
                direction,
                unicodeBidi,
                value,
                boxSizing,
                containment);
    }

    /// This presence with `box-sizing` replaced.
    public Presence boxSizing(BoxSizing value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                order,
                direction,
                unicodeBidi,
                compositing,
                value,
                containment);
    }

    /// This presence with `container-type` and `container-name` replaced.
    public Presence containment(Containment value) {
        return new Presence(
                display,
                aspectRatio,
                visibility,
                pointerEvents,
                userSelect,
                zIndex,
                order,
                direction,
                unicodeBidi,
                compositing,
                boxSizing,
                value);
    }

    /// Whether `aspect-ratio` is `auto`.
    public boolean hasAutoRatio() {
        return Double.isNaN(aspectRatio);
    }

    /// Whether the box's own drawing is painted.
    public boolean isVisible() {
        return visibility == Visibility.VISIBLE;
    }

    /// Whether the pointer finds this box: it is seen, and does not let the
    /// pointer through.
    public boolean isHit() {
        return isVisible() && pointerEvents == PointerEvents.AUTO;
    }

    /// `visibility`.
    public enum Visibility {
        /// Drawn.
        VISIBLE,
        /// Not drawn, not hit and not focused, and its room kept. `collapse`
        /// reads as this: a flex box has no table rows to collapse.
        HIDDEN;

        /// The keyword, or null: `visible`, `hidden`, or `collapse` as hidden.
        public static @Nullable Visibility parse(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "visible" -> VISIBLE;
                case "hidden", "collapse" -> HIDDEN;
                default -> null;
            };
        }
    }

    /// `pointer-events`.
    public enum PointerEvents {
        /// The pointer finds the box.
        AUTO,
        /// The pointer passes through to what is under the box. Its children
        /// are found when they say `auto`.
        NONE;

        /// The keyword, or null: `auto`, `none`, or one of SVG's values, which
        /// a box reads as `auto`.
        public static @Nullable PointerEvents parse(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "auto",
                        "all",
                        "visible",
                        "visiblepainted",
                        "visiblefill",
                        "visiblestroke",
                        "painted",
                        "fill",
                        "stroke",
                        "bounding-box" -> AUTO;
                case "none" -> NONE;
                default -> null;
            };
        }
    }

    /// `user-select`.
    public enum UserSelect {
        /// As the content allows: a document's text is selectable.
        AUTO,
        /// Selectable, which is what `auto` is everywhere here.
        TEXT,
        /// Not selectable.
        NONE,
        /// Selected whole by any click.
        ALL;

        /// The keyword, or null: `auto`, `text`, `none`, `all`, or `contain` as
        /// `auto`.
        public static @Nullable UserSelect parse(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "auto", "contain" -> AUTO;
                case "text" -> TEXT;
                case "none" -> NONE;
                case "all" -> ALL;
                default -> null;
            };
        }
    }
}
