package io.github.digitalsmile.goldberry.widgets.core.canvas;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Painter;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// An immediate-mode drawing surface — `docs/core-widgets.md` §1's `canvas`.
///
/// ```java
/// new Canvas((frame, size) -> {
///     frame.fillRect(0, 0, size.width(), size.height() / 2, 0xFF88C0D0);
/// });
/// ```
///
/// ```kdl
/// canvas id="plot" class="chart"
/// ```
///
/// **The escape hatch, and the substrate.** `content-widgets.md` §3 builds every
/// chart on this rather than on a chart engine, which is what lets a chart inherit
/// the theme, the text stack, hit testing and the golden corpus. It is also what
/// an application reaches for when the catalog has no widget for what it wants —
/// a waveform, a seating plan, a colour wheel.
///
/// ## It is a box first
///
/// Background, border, radius, padding and every layout property are the
/// stylesheet's, exactly as for `panel`. The painter draws **inside the padding**
/// and is clipped to it, so `canvas { padding: 8px; background: var(--gb-surface) }`
/// is a framed drawing surface and not a surprise
/// (ADR-0193).
///
/// ## It has no size of its own
///
/// A canvas is not measured: it takes the size the layout gives it, and the
/// painter is told what that turned out to be. A canvas in a `row` with nothing
/// else to size it is zero wide, which is a stylesheet's job to fix
/// (`flex-grow: 1`, a `height`) and not a default this widget can guess — an
/// intrinsic size would be a number invented by the toolkit and drawn by the
/// application.
///
/// ## It handles input, when it is given something to handle it with
///
/// An [Input] beside the [Painter] and nothing else: the toolkit's own
/// [PointerEvent] and [KeyEvent] arrive as they are, with
/// [PointerEvent#content()] measured from the same corner the painter draws at.
/// A drag that leaves the canvas keeps reporting, because the router captures the
/// pointer on press like it does for any other widget (ADR-0281).
///
/// A canvas with no `Input` is exactly what it was before: a styled, sized
/// surface that draws and hears nothing, and not a Tab stop.
///
/// ## Markup names no painter yet
///
/// A `canvas` node inflates to a styled, sized surface that draws nothing. The
/// painter is Java, and naming one from a document would need the indirection
/// `icon` and `action` use — a registry the application owns
/// (ADR-0043).
/// That is filed rather than guessed at, because the shape of the registry
/// depends on whether a painter is a value or a method and nothing has needed
/// one yet.
///
/// @param painter    what to draw, or null for a surface that draws nothing
/// @param input      what to do with pointer and key events, or null to hear none
/// @param attributes `id` and `class`, exactly as on the other primitives
@Markup("canvas")
public record Canvas(@Nullable Painter painter, @Nullable Input input, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Canvas>, Handles, Semantics {

    public Canvas {
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A canvas that draws `painter` and hears nothing.
    public Canvas(Painter painter) {
        this(painter, null, Attributes.NONE);
    }

    /// A canvas that draws `painter`, with attributes and no input.
    public Canvas(Painter painter, Attributes attributes) {
        this(painter, null, attributes);
    }

    /// A canvas that draws and listens.
    public Canvas(Painter painter, Input input) {
        this(painter, input, Attributes.NONE);
    }

    /// This canvas, listening through `value`.
    ///
    /// A wither rather than a fluent `onPointerDown(…)` per kind: a canvas tool is
    /// a state machine over a *sequence* of events, and five callbacks that have
    /// to share state between them is five closures over the same mutable object.
    public Canvas input(Input value) {
        return new Canvas(painter, value, attributes);
    }

    @Override
    public String cssType() {
        return "canvas";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public Canvas withAttributes(Attributes value) {
        return new Canvas(painter, input, value);
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (input != null) {
            input.onPointer(event);
        }
    }

    @Override
    public void onKey(KeyEvent event) {
        if (input != null) {
            input.onKey(event);
        }
    }

    @Override
    public void onText(TextEvent event) {
        if (input != null) {
            input.onText(event);
        }
    }

    /// A picture of data a reader reaches with the keyboard, which is what a
    /// canvas is whatever it happens to be drawing.
    ///
    /// Declared whether or not this canvas is focusable, because a chart nobody
    /// can Tab to is still a figure — `SemanticsSweepTest` asks the question of
    /// the focusable ones, and answering it only for those would be answering the
    /// test rather than the reader.
    @Override
    public Role role() {
        return Role.FIGURE;
    }

    /// What the application called it — see [Input#accessibleName()].
    @Override
    public @Nullable String accessibleName() {
        return input == null ? null : input.accessibleName();
    }

    /// Focusable only when there is something to deliver a key *to*.
    ///
    /// A canvas drawing a chart would otherwise be a Tab stop that does nothing,
    /// which is a keyboard trap with no exit and the thing §2.2's "everything
    /// reachable" is least served by.
    @Override
    public boolean isFocusable() {
        return input != null && input.focusable();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // No children: a canvas is a leaf that draws. Boxes inside it would be
        // laid out by Yoga and painted *over* whatever the painter drew, which is
        // a `stack` and not a canvas.
        return Box.of().style(style).painting(painter);
    }

    /// Builds a `canvas` from markup — see the class note on why it names no
    /// painter.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Canvas(null, null, Attributes.of(node));
    }
}
