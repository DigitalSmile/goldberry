package dev.goldberry.widgets.core.canvas;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.PreeditEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Overflow;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.CanvasStyle;
import dev.goldberry.paint.Painter;
import dev.goldberry.paint.StyledPainter;
import dev.goldberry.paint.tree.ContainingBlock;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A surface the application draws on itself, with a painter written in Java.
///
/// ```java
/// new Canvas((frame, size) -> {
///     frame.fillRect(0, 0, size.width(), size.height() / 2, 0xFF88C0D0);
/// });
/// ```
///
/// ```java
/// new Canvas((frame, size, style) -> {                 // one parameter more
///     Paragraph.of(style.font(), "Revenue").paint(frame, 0, 0, size.width(), style.ink());
/// });
/// ```
///
/// ```kdl
/// canvas id="plot" class="chart"
/// ```
///
/// **The escape hatch, and the substrate.** Every chart in the catalogue is built
/// on this rather than on a chart engine, which is what lets a chart inherit
/// the theme, the text stack, hit testing and the golden corpus. It is also what
/// an application reaches for when the catalogue has no widget for what it wants —
/// a waveform, a seating plan, a colour wheel.
///
/// ## It can be told what the cascade resolved
///
/// A three-parameter painter is a [StyledPainter] and is handed a
/// [dev.goldberry.paint.CanvasStyle]: the node's **own**
/// resolved font and `color`, this frame's time, and whether the user asked for
/// less movement. So `canvas { font-family: Inter; color: var(--gb-text) }` reaches
/// the drawing, and canvas text follows a theme switch instead of naming a font.
///
/// The compiler picks between the two forms by arity and neither is second
/// class: a painter with nothing to ask the cascade stays a two-parameter
/// [Painter].
///
/// ## It is a box first
///
/// Background, border, radius, padding and every layout property are the
/// stylesheet's, exactly as for `panel`. The painter draws **inside the padding**
/// and is clipped to it, so `canvas { padding: 8px; background: var(--gb-surface) }`
/// is a framed drawing surface and not a surprise. The call is bracketed in
/// `save` and `restore`, so a painter may set a clip or a transform and leave it set.
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
/// pointer on press like it does for any other widget.
///
/// A canvas with no `Input` is exactly what it was before: a styled, sized
/// surface that draws and hears nothing, and not a Tab stop.
///
/// ## It can keep the frame loop turning
///
/// The frame loop is idle when nothing moves, so a painter drawn from
/// [CanvasStyle#nowMillis()] is painted once and left there unless the canvas
/// asks for the next frame. [#animating(Predicate)] is how it asks — a predicate
/// over the same [CanvasStyle] the painter was handed, so a settle that ends by
/// itself can say so without the application rebuilding anything:
///
/// ```java
/// var mounted = clock.nowMillis();
/// new Canvas(floor::paint).animating(style -> style.nowMillis() - mounted < LAST_TILE_LANDS)
/// ```
///
/// A timer calling `host.repaint()` would do it too, and would repaint the whole
/// window on a clock the frame pacer cannot see.
///
/// ## It can carry real widgets where the painter put things
///
/// A painted rectangle has no focus ring, no accessible name and no place in
/// the Tab order. Where something on the drawing has to be each of those, a
/// week grid's events or a board's handles, [#overlay(Function)] places a
/// widget over it:
///
/// ```java
/// new Canvas(week::paint).overlay(size -> week.layout(size).blocks().stream()
///         .map(block -> new Positioned(eventButton(block).keyed(block.id()), block.rect()))
///         .toList())
/// ```
///
/// The function is handed the size of the canvas's content box and answers
/// with [Positioned] widgets, each at a rectangle in the painter's own
/// coordinates. They are ordinary widgets inside the canvas's box: drawn over
/// the painting and clipped to the content box, scrolled with the canvas when
/// a `scroll` moves it, hit-tested before it, so a press on one goes to it and
/// a press beside it reaches [Input], focusable in the order of the list, and
/// listed in the semantics tree under the canvas. The list is asked for again
/// whenever the content box changes size and whenever the canvas is rebuilt.
/// Give each widget a key: a widget whose key comes back keeps its element, and
/// with it its focus and its state.
///
/// The widgets arrive a frame after the canvas is first laid out, because the
/// size they are placed by is the one the last frame measured.
///
/// ## Markup names no painter yet
///
/// A `canvas` node inflates to a styled, sized surface that draws nothing. The
/// painter is Java, and naming one from a document would need the indirection
/// `icon` and `action` use — a registry the application owns. There is no such
/// registry yet, because its shape depends on whether a painter is a value or a
/// method and nothing has needed one.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#canvas).
///
/// @param painter    what to draw, or null for a surface that draws nothing. A
///                   [StyledPainter] is given the node's resolved style; a plain
///                   [Painter] is not asked for one.
/// @param input      what to do with pointer and key events, or null to hear none
/// @param attributes `id` and `class`, exactly as on the other primitives
/// @param animating  whether the canvas wants another frame after this one, or
///                   null for a still drawing — [#animating(Predicate)]
/// @param overlay    the widgets to place over the drawing, given the content
///                   box's size, or null for none — [#overlay(Function)]
@Markup("canvas")
public record Canvas(
        @Nullable Painter painter,
        @Nullable Input input,
        Attributes attributes,
        @Nullable Predicate<CanvasStyle> animating,
        @Nullable Function<LogicalSize, List<Positioned>> overlay)
        implements Widget.Leaf, Styled, Paints, Attributed<Canvas>, Handles, Semantics {

    /// Written out so that the parameters taking null for a default can say so.
    public Canvas(
            @Nullable Painter painter,
            @Nullable Input input,
            @Nullable Attributes attributes,
            @Nullable Predicate<CanvasStyle> animating,
            @Nullable Function<LogicalSize, List<Positioned>> overlay) {
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.painter = painter;
        this.input = input;
        this.attributes = attributes;
        this.animating = animating;
        this.overlay = overlay;
    }

    /// A canvas with no widgets over it — every canvas written before
    /// [#overlay(Function)] existed.
    public Canvas(
            @Nullable Painter painter,
            @Nullable Input input,
            @Nullable Attributes attributes,
            @Nullable Predicate<CanvasStyle> animating) {
        this(painter, input, attributes, animating, null);
    }

    /// A canvas that draws a still picture — every canvas written before
    /// [#animating(Predicate)] existed.
    public Canvas(@Nullable Painter painter, @Nullable Input input, Attributes attributes) {
        this(painter, input, attributes, null);
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

    /// A canvas whose painter is told what the cascade resolved.
    ///
    /// The same three constructors again, one overload each. They exist for the
    /// compiler rather than for the reader: a three-parameter lambda is not a
    /// [Painter], so without a [StyledPainter] parameter to target it there would
    /// be nothing for `new Canvas((frame, size, style) -> …)` to mean. Being the
    /// more specific type, these also take `null` without an ambiguity.
    public Canvas(StyledPainter painter) {
        this(painter, null, Attributes.NONE);
    }

    /// [#Canvas(StyledPainter)], with attributes.
    public Canvas(StyledPainter painter, Attributes attributes) {
        this(painter, null, attributes);
    }

    /// [#Canvas(StyledPainter)], listening.
    public Canvas(StyledPainter painter, Input input) {
        this(painter, input, Attributes.NONE);
    }

    /// [#Canvas(StyledPainter)], listening, with attributes — the canonical
    /// constructor's overload, without which a method reference to a
    /// three-parameter painter has nothing to match at the three-argument call
    /// site.
    public Canvas(@Nullable StyledPainter painter, @Nullable Input input, Attributes attributes) {
        this((Painter) painter, input, attributes);
    }

    /// This canvas, listening through `value`.
    ///
    /// A wither rather than a fluent `onPointerDown(…)` per kind: a canvas tool is
    /// a state machine over a *sequence* of events, and five callbacks that have
    /// to share state between them is five closures over the same mutable object.
    public Canvas input(Input value) {
        return new Canvas(painter, value, attributes, animating, overlay);
    }

    /// This canvas, asking for another frame for as long as `value` says so.
    ///
    /// Read **once per frame, straight after the painter is bound**, with the
    /// same [CanvasStyle] the painter was given — the same `nowMillis`, so the
    /// frame that draws the last tile landing is the frame that answers false and
    /// the loop goes quiet on the next one rather than a frame later.
    ///
    /// A predicate rather than a boolean, so an animation that ends can stop by
    /// itself. `style -> true` is a loop; `style -> !style.reducedMotion()` is a
    /// loop that stops for a user who asked for less movement, which is the
    /// painter's to honour and not the canvas's to guess.
    ///
    /// @param value the question, or null for a still drawing
    public Canvas animating(@Nullable Predicate<CanvasStyle> value) {
        return new Canvas(painter, input, attributes, value, overlay);
    }

    /// This canvas, with the widgets `value` places over its drawing — see the
    /// class note.
    ///
    /// Called with the size of the content box, the rectangle the painter is
    /// told about, once that is known and again whenever it changes or the
    /// canvas is rebuilt. It answers in the painter's coordinates, so the
    /// arithmetic that placed a block in the painting places its widget too.
    /// The order of the list is the Tab order; a caller whose list is in
    /// visual order has a canvas whose focus moves in visual order.
    ///
    /// @param value the widgets for a size, or null for none
    public Canvas overlay(@Nullable Function<LogicalSize, List<Positioned>> value) {
        return new Canvas(painter, input, attributes, animating, value);
    }

    /// The layer that holds the [Positioned] widgets, or nothing when this
    /// canvas has none.
    @Override
    public List<Widget> children() {
        return overlay == null ? List.of() : List.of(new CanvasLayer(overlay));
    }

    @Override
    public String cssType() {
        return "canvas";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Canvas withAttributes(Attributes value) {
        return new Canvas(painter, input, value, animating, overlay);
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

    /// Whether the platform's input method is on while this canvas has the
    /// keyboard — [Input#wantsText()], and false when there is no input at all.
    @Override
    public boolean wantsTextInput() {
        return input != null && input.wantsText();
    }

    /// Passed through so a painter can draw a caret only while it is being typed
    /// into.
    @Override
    public void onFocusChanged(boolean focused, boolean fromKeyboard) {
        if (input != null) {
            input.onFocusChanged(focused, fromKeyboard);
        }
    }

    @Override
    public void onText(TextEvent event) {
        if (input != null) {
            input.onText(event);
        }
    }

    /// Passed through so an editor over a canvas can draw what an input method is
    /// composing.
    @Override
    public void onPreedit(PreeditEvent event) {
        if (input != null) {
            input.onPreedit(event);
        }
    }

    /// Where this canvas's caret is, so the platform can place a candidate
    /// window — [Input#caretArea()]. Nothing, when the canvas hears nothing.
    @Override
    public Optional<LogicalRect> caretArea() {
        return input == null ? Optional.empty() : input.caretArea();
    }

    /// Where the caret is within it — [Input#caretOffsetIn].
    @Override
    public double caretOffsetIn(LogicalRect area) {
        return input == null ? 0 : input.caretOffsetIn(area);
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
    /// which is a keyboard trap with no exit and the opposite of everything
    /// being reachable by keyboard.
    @Override
    public boolean isFocusable() {
        return input != null && input.focusable();
    }

    /// The one place the resolved style is in hand and the painter is too.
    ///
    /// A [StyledPainter] is **bound here** rather than at paint time, because
    /// what it is given has to be a snapshot: the token and font accessors on
    /// [Context] answer for the node currently being rendered, so a context read
    /// during the paint pass would answer for whichever node rendered last.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var painting = painter instanceof StyledPainter styled ? styled.bound(context.canvasStyle(style)) : painter;
        var box = Box.of().style(style);
        // A box paints nothing until told otherwise, so a canvas with no painter
        // (one from markup) is that box -- `Box.painting` does not take null yet.
        box = painting == null ? box : box.painting(painting);
        if (children.isEmpty()) {
            return box;
        }
        // The overlay's layer, and nothing else: a canvas lays out no children
        // of its own. It is pinned to the content box, out of flow, so the
        // canvas is still sized by its stylesheet alone, and it clips there, as
        // the painter is clipped. A box's children are painted after its own
        // content, so the widgets are drawn over the painting and hit first.
        var layer = new Box[children.size()];
        for (var i = 0; i < layer.length; i++) {
            layer[i] = children.get(i)
                    .position(Position.ABSOLUTE)
                    .inset(ContainingBlock.inContentBox(
                            Insets.all(Length.points(0)),
                            style.padding(),
                            style.decoration().border()))
                    .overflow(Overflow.HIDDEN);
        }
        return box.children(layer);
    }

    /// Whether [#animating(Predicate)] asks for another frame.
    ///
    /// The snapshot is taken again rather than shared with [#render]: it is a
    /// record of four values read off the context, and the renderer asks this
    /// inside the same window `render` ran in, so both see this node's style and
    /// this frame's clock.
    @Override
    public boolean isAnimating(ComputedStyle style, Context context) {
        return animating != null && animating.test(context.canvasStyle(style));
    }

    /// Builds a `canvas` from markup — see the class note on why it names no
    /// painter.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Canvas(null, null, Attributes.of(node));
    }
}
