package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.paint.Dash;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.Gradient;
import io.github.digitalsmile.goldberry.paint.Path;
import io.github.digitalsmile.goldberry.paint.Stroke;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.canvas.Canvas;
import io.github.digitalsmile.goldberry.widgets.core.canvas.Input;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Canvas** screen: the escape hatch, and what it is now able to do.
///
/// ## Why this screen exists
///
/// The charts are built on the *painter* — `Paints`, `Painter` and `Box.painting`
/// — and not on the `canvas` widget, so a wall of them demonstrates the drawing
/// primitive and says nothing about `canvas` itself. What `canvas` adds is the
/// thing an application reaches for when the catalogue has no widget for what it
/// wants: a surface it draws on *and* one it hears (ADR-0281).
///
/// Three cards, each pinning one claim:
///
/// 1. **The paint values are the toolkit's.** Nothing on this screen names a
///    `:natives` type, which was not true of any drawing in this repository
///    before ADR-0277 — the five widgets that drew curves all imported
///    `BlendPath`.
/// 2. **A dash is Goldberry's own arithmetic.** Blend2D stores a dash array and
///    never strokes with it, so the dashed ring here is a solid stroke of a path
///    that was cut up first (ADR-0278). The dotted rings are a **short** dash
///    rather than SVG's zero-length one, because this screen is where we found
///    out that Blend2D will not ink a zero-length sub-path.
/// 3. **Input lands where the ink is.** The pointer card has padding, and the
///    crosshair follows the pointer exactly — which it would not if it read
///    `local()` instead of `content()`.
///
/// ## The data is a constant, and the pointer starts nowhere
///
/// Like [Charts], this screen is also a golden image. Every coordinate below is
/// written down, and the two interactive cards draw **nothing extra** until a
/// pointer touches them — so the picture at rest is the same one every time,
/// on every machine.
public record CanvasScreen() implements Widget.Stateful {

    private static final String NOTE =
            "§1's `canvas` — the one widget an application writes its own drawing into. Everything"
                    + " here is `paint.Path`, `Stroke` and `Gradient`: no drawing on this screen"
                    + " names a type from the native layer, and none of it could have been written"
                    + " that way before. Move the pointer over the lower two cards.";

    /// The chart palette's first three slots, so this screen sits beside the
    /// others rather than inventing colours of its own.
    private static final int INK = 0xFF88C0D0;

    private static final int ACCENT = 0xFFA3BE8C;

    private static final int WARN = 0xFFEBCB8B;

    private static final int MUTED = 0xFF4C566A;

    @Override
    public State<?> createState() {
        return new CanvasState();
    }

    static final class CanvasState extends State<CanvasScreen> {

        /// Where the pointer last was, in the pointer card's own coordinates, or
        /// null when it has never been over it.
        ///
        /// Null rather than a sentinel: "nowhere" is a real state — it is the one
        /// the golden image is taken in — and a canvas that drew a crosshair at
        /// (0, 0) until touched would be drawing a lie.
        private float[] at;

        /// How far the plan has been dragged, and how far it has been zoomed.
        private float panX;

        private float panY;

        private float zoom = 1;

        private float dragX;

        private float dragY;

        private static Attributes id(String id, String... classes) {
            return new Attributes(id, Set.of(classes), id);
        }

        private static Widget caption(String text) {
            return new Text(text, Attributes.NONE.classes("caption"));
        }

        private static Widget captioned(String title, Attributes attributes, Widget... parts) {
            var children = new ArrayList<Widget>(parts.length + 1);
            children.add(new Text(title, Attributes.NONE.classes("card-title")));
            children.addAll(List.of(parts));
            return new Card(List.copyOf(children), attributes.classes("wall-card"));
        }

        // --- the three drawings ----------------------------------------------

        /// Every kind of segment a [Path] has, and every way a [Stroke] draws one.
        ///
        /// Static, and deliberately so: this is the card that has to look the same
        /// in a golden image as it does on screen.
        private static void paintPaths(Frame frame, LogicalSize size) {
            var width = size.width();
            var height = size.height();

            // A filled shape with a gradient through it -- `Gradient.fade`, which
            // repeats the RGB at the transparent end so the ramp thins out rather
            // than going through grey (ADR-0207).
            var hill = Path.builder()
                    .moveTo(0, height)
                    .lineTo(0, height * 0.55f)
                    .cubicTo(width * 0.25f, height * 0.2f, width * 0.45f, height * 0.9f, width * 0.6f, height * 0.5f)
                    .cubicTo(width * 0.75f, height * 0.2f, width * 0.85f, height * 0.35f, width, height * 0.3f)
                    .lineTo(width, height)
                    .close()
                    .build();
            frame.fillPath(hill, Gradient.fade(0, height * 0.2f, 0, height, CssColor.fade(INK, 0.55)));

            // The same curve as a stroke, round-capped and round-joined -- the pen
            // every line chart in the catalogue draws with.
            var ridge = Path.builder()
                    .moveTo(0, height * 0.55f)
                    .cubicTo(width * 0.25f, height * 0.2f, width * 0.45f, height * 0.9f, width * 0.6f, height * 0.5f)
                    .cubicTo(width * 0.75f, height * 0.2f, width * 0.85f, height * 0.35f, width, height * 0.3f)
                    .build();
            frame.strokePath(ridge, Stroke.round(2), INK);

            // A dashed baseline. Blend2D would have drawn this solid: it keeps a
            // dash array and its stroker never reads one, so the dashes are cut
            // into the path before it is stroked (ADR-0278).
            frame.strokePath(
                    Path.line(0, height * 0.82f, width, height * 0.82f),
                    Stroke.of(1).dashed(4, 4),
                    MUTED);

            // A ring, and a dotted ring around it. The dots are a **short** dash
            // and not a zero-length one: `Dasher` emits the zero-length sub-path
            // SVG asks for, and Blend2D declines to ink it -- which this screen is
            // how we found out (ADR-0278).
            frame.strokePath(Path.circle(width * 0.16f, height * 0.28f, 14), Stroke.round(2), ACCENT);
            frame.strokePath(
                    Path.circle(width * 0.16f, height * 0.28f, 22),
                    Stroke.round(2).dash(Dash.of(0.5, 5)),
                    ACCENT);

            // A rounded rectangle and an arc, which were `RoundRect` and `Arc` and
            // are `Path` factories now.
            frame.strokePath(Path.roundRect(width - 76, 14, 60, 30, 8), Stroke.of(1.5), WARN);
            frame.strokePath(
                    Path.arc(width - 46, height * 0.55f, 18, -Math.PI / 2, Math.PI * 1.4), Stroke.round(3), WARN);
        }

        /// A grid, and a crosshair wherever the pointer is.
        ///
        /// The card has padding, so this is the drawing that would be visibly
        /// wrong if input were reported from the border box instead of the
        /// content box — the crosshair would trail the pointer by the padding
        /// (ADR-0281).
        private void paintPointer(Frame frame, LogicalSize size) {
            var width = size.width();
            var height = size.height();

            var grid = Path.builder();
            for (var x = 0f; x <= width; x += 24) {
                grid.moveTo(x, 0).lineTo(x, height);
            }
            for (var y = 0f; y <= height; y += 24) {
                grid.moveTo(0, y).lineTo(width, y);
            }
            frame.strokePath(grid.build(), Stroke.of(1), CssColor.fade(MUTED, 0.5));

            if (at == null) {
                return;
            }
            frame.strokePath(
                    Path.builder()
                            .moveTo(0, at[1])
                            .lineTo(width, at[1])
                            .moveTo(at[0], 0)
                            .lineTo(at[0], height)
                            .build(),
                    Stroke.of(1).dashed(3, 3),
                    ACCENT);
            frame.fillPath(Path.circle(at[0], at[1], 4), ACCENT);
        }

        /// A plan the wheel zooms and a drag moves.
        ///
        /// The transform is the application's arithmetic, not the frame's: what
        /// the toolkit supplies is the wheel's detents and a drag that keeps
        /// reporting after it leaves the box.
        private void paintPlan(Frame frame, LogicalSize size) {
            var width = size.width();
            var height = size.height();
            var cx = width / 2 + panX;
            var cy = height / 2 + panY;

            frame.strokePath(
                    Path.roundRect(cx - 54 * zoom, cy - 30 * zoom, 108 * zoom, 60 * zoom, 6 * zoom),
                    Stroke.of(1.5),
                    INK);
            frame.strokePath(
                    Path.line(cx - 54 * zoom, cy, cx + 54 * zoom, cy),
                    Stroke.of(1).dashed(5, 4),
                    MUTED);
            frame.fillPath(Path.circle(cx, cy, 5 * zoom), ACCENT);
            frame.strokePath(Path.circle(cx, cy, 26 * zoom), Stroke.round(1.5).dash(Dash.of(0.5, 6)), WARN);
        }

        @Override
        public Widget build(BuildContext context) {
            return new Wall(
                    "canvas",
                    "Canvas",
                    NOTE,
                    2,
                    List.of(
                            captioned(
                                    "Paths, strokes and a ramp",
                                    id("paths-card"),
                                    new Canvas(CanvasState::paintPaths, id("paths")),
                                    caption("Lines, cubics, arcs, a rounded rectangle, a dashed baseline and a"
                                            + " dotted ring — and a gradient that thins out rather than going"
                                            + " through grey. Not one of them names a native type.")),
                            captioned(
                                    "Where the pointer is",
                                    id("pointer-card"),
                                    new Canvas(
                                            this::paintPointer,
                                            new Input() {
                                                @Override
                                                public void onPointer(PointerEvent event) {
                                                    var local = event.content();
                                                    setState(() -> at = event.kind() == PointerEvent.Kind.EXITED
                                                            ? null
                                                            : new float[] {local.x(), local.y()});
                                                }

                                                @Override
                                                public String accessibleName() {
                                                    return "Pointer position";
                                                }
                                            },
                                            id("pointer")),
                                    caption("This card has padding, and the crosshair still lands under the"
                                            + " pointer: a canvas reads content() — the rectangle the painter"
                                            + " was given — and not the box it sits in.")),
                            captioned(
                                    "A wheel and a drag",
                                    id("plan-card"),
                                    new Canvas(
                                            this::paintPlan,
                                            new Input() {
                                                @Override
                                                public void onPointer(PointerEvent event) {
                                                    switch (event.kind()) {
                                                        case PRESSED ->
                                                            setState(() -> {
                                                                dragX = event.x() - panX;
                                                                dragY = event.y() - panY;
                                                            });
                                                        case MOVED -> {
                                                            if (!Float.isNaN(event.pressX())) {
                                                                setState(() -> {
                                                                    panX = event.x() - dragX;
                                                                    panY = event.y() - dragY;
                                                                });
                                                            }
                                                        }
                                                        case WHEEL ->
                                                            setState(() -> zoom = (float) Math.clamp(
                                                                    zoom * Math.pow(1.1, -event.ticksY()), 0.4, 3.0));
                                                        default -> {}
                                                    }
                                                    event.consume();
                                                }

                                                @Override
                                                public String accessibleName() {
                                                    return "Plan, pan and zoom";
                                                }
                                            },
                                            id("plan")),
                                    caption("Drag to move it and roll the wheel to scale it. The drag keeps"
                                            + " reporting after it leaves the card — the router captures the"
                                            + " pointer on press, so nothing here asked for it."))));
        }
    }
}
