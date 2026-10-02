package dev.goldberry.gpu.view;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Stack;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;
import dev.goldberry.widgets.overlay.message.Message;

/// A 3D view: a box the GPU draws into, with an application's
/// [Canvas3dRenderer].
///
/// ```java
/// new Canvas3d(new Cube()).continuous(true).depth(Canvas3d.Depth.D16)
/// ```
///
/// ```kdl
/// canvas3d renderer="cube" continuous=#true depth="d16"
/// ```
///
/// A leaf sized like an `image` or a `canvas`: by its stylesheet, `width` and
/// `height`. It is a GPU layer, so what is painted after it is over
/// it, it is opaque, and a clip cuts it to a rectangle. Where the window
/// presents through the GPU it is composited; elsewhere -- headless, a popup,
/// `goldberry.gpu.composite=never` -- it is rendered and read back.
///
/// **When it is drawn.** A `continuous` canvas is drawn on every frame, and
/// keeps the window drawing frames while it is shown: a spinning model, a game.
/// Otherwise it is drawn once, then again when its size changes and when its
/// [#revision] does: a model viewer that redraws when its model or camera
/// changes rebuilds the widget with the next revision. Between those the last
/// picture is shown again, and nothing is rendered.
///
/// **Where there is no GPU** -- `goldberry.gpu=off`, a device that cannot be
/// made, a canvas inside an `opacity` group -- the box is filled with
/// `--gb-canvas3d-unavailable` and, in a running window, a notice says why,
/// as `web-view` does without its engine.
///
/// The renderer is the application's and is kept by the canvas while it is
/// mounted: initialised at its first frame, disposed when the canvas leaves the
/// tree, or when the widget is rebuilt with another renderer or depth.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
///
/// @param renderer   what draws the picture
/// @param continuous whether it is drawn on every frame
/// @param depth      the depth target the renderer draws with, if any
/// @param revision   what, changed, has an on-demand canvas drawn again
/// @param attributes id, classes and key
@Markup("canvas3d")
public record Canvas3d(Canvas3dRenderer renderer, boolean continuous, Depth depth, long revision, Attributes attributes)
        implements Widget.Stateful, Attributed<Canvas3d> {

    /// The depth target a canvas gives its renderer.
    public enum Depth {
        /// None: a renderer that culls, or draws in order.
        NONE(null),
        /// 16-bit, which a scene of modest depth range needs and every GPU has.
        D16(TextureFormat.D16_UNORM),
        /// 32-bit float, for a deep scene.
        D32(TextureFormat.D32_FLOAT);

        private final @Nullable TextureFormat format;

        Depth(@Nullable TextureFormat format) {
            this.format = format;
        }

        /// The texture format, or null for none.
        @Nullable
        TextureFormat format() {
            return format;
        }

        /// The depth markup's `depth=` names: `none`, `d16` or `d32`; absent
        /// is none.
        ///
        /// @throws IllegalArgumentException for any other name
        public static Depth named(@Nullable String name) {
            if (name == null) {
                return NONE;
            }
            return switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "none" -> NONE;
                case "d16" -> D16;
                case "d32" -> D32;
                default -> throw new IllegalArgumentException("depth=\"" + name + "\": it is none, d16 or d32");
            };
        }
    }

    /// Checks nothing is missing.
    public Canvas3d {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// A canvas drawn on demand, with no depth target.
    public Canvas3d(Canvas3dRenderer renderer) {
        this(renderer, false, Depth.NONE, 0, Attributes.NONE);
    }

    /// The same canvas, drawn on every frame or on demand.
    public Canvas3d continuous(boolean value) {
        return new Canvas3d(renderer, value, depth, revision, attributes);
    }

    /// The same canvas, with `value` as its depth target.
    public Canvas3d depth(Depth value) {
        return new Canvas3d(renderer, continuous, value, revision, attributes);
    }

    /// The same canvas at `value`: a revision other than the last one drawn
    /// has it drawn again.
    public Canvas3d revision(long value) {
        return new Canvas3d(renderer, continuous, depth, value, attributes);
    }

    @Override
    public Canvas3d withAttributes(Attributes value) {
        return new Canvas3d(renderer, continuous, depth, revision, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new Canvas3dState();
    }

    /// `canvas3d renderer="name" continuous=#true depth="d16" revision=3`:
    /// `renderer=` names a [Canvas3dRenderer] the application bound, and the
    /// rest are optional.
    ///
    /// @throws IllegalArgumentException when it has children, or `depth=` is
    ///                                  not a depth
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("canvas3d takes no children");
        }
        return new Canvas3d(
                // Required, and refused with the constructor's own message when absent.
                Objects.requireNonNull(wiring.handle(node, "renderer", Canvas3dRenderer.class), "renderer"),
                node.booleanProperty("continuous"),
                Depth.named(node.stringProperty("depth")),
                (long) node.numberProperty("revision", 0),
                Attributes.of(node));
    }

    /// The canvas's layer, kept while it is mounted, and what it says when it
    /// cannot be drawn.
    static final class Canvas3dState extends State<Canvas3d> implements Canvas3dSurface.Owner {

        static final String GPU_OFF = "The GPU is off (goldberry.gpu=off), so this 3D view is not drawn.";
        static final String NO_GPU = "There is no GPU to draw this 3D view with here: it is inside a translucent"
                + " group, or its window cannot use the GPU.";
        static final String FAILED = "The GPU could not draw this 3D view. The log says why.";

        private @Nullable Canvas3dLayer layer;
        private @Nullable Host host;
        private @Nullable String reason;

        /// The window's clock when the canvas was first drawn, and when its
        /// revision last changed, or NaN before.
        private double firstMillis = Double.NaN;

        private double revisionMillis = Double.NaN;
        private long drawnRevision;

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var canvas = widget();
            var surface =
                    new Canvas3dSurface(layer(), canvas.continuous(), canvas.revision(), this, canvas.attributes());
            var why = reason;
            if (why == null) {
                return surface;
            }
            return new Stack(
                    List.of(
                            surface,
                            new Message(Message.Kind.WARNING, why)
                                    .withAttributes(Attributes.NONE.classes("canvas3d-notice"))),
                    Attributes.NONE.classes("canvas3d-stage"));
        }

        /// The layer, made the first time and after a new renderer or depth.
        private Canvas3dLayer layer() {
            var current = layer;
            if (current == null) {
                current =
                        new Canvas3dLayer(widget().renderer(), widget().depth().format());
                layer = current;
            }
            return current;
        }

        @Override
        protected void didUpdateWidget(Canvas3d previous) {
            var current = widget();
            if (previous.renderer() != current.renderer() || previous.depth() != current.depth()) {
                release();
            } else if (previous.revision() != current.revision() && layer != null) {
                layer.redraw();
            }
        }

        @Override
        public long nanos(double nowMillis, boolean continuous, long revision) {
            if (Double.isNaN(firstMillis)) {
                firstMillis = nowMillis;
            }
            if (Double.isNaN(revisionMillis) || revision != drawnRevision) {
                revisionMillis = nowMillis;
                drawnRevision = revision;
            }
            var at = continuous ? nowMillis : revisionMillis;
            return (long) Math.max(0, (at - firstMillis) * 1_000_000);
        }

        /// What the notice says after a paint: nothing when the layer was
        /// drawn; otherwise whether the GPU is off, missing here, or failed.
        ///
        /// @param gpu the `goldberry.gpu` property, or null
        static @Nullable String reason(boolean drawn, boolean hasGpu, @Nullable String gpu) {
            if (drawn) {
                return null;
            }
            if (hasGpu) {
                return FAILED;
            }
            return gpu != null && gpu.trim().equalsIgnoreCase("off") ? GPU_OFF : NO_GPU;
        }

        @Override
        public void shown(boolean drawn, boolean hasGpu) {
            var why = reason(drawn, hasGpu, System.getProperty("goldberry.gpu"));
            var current = host;
            if (Objects.equals(reason, why) || current == null) {
                return;
            }
            // From inside a paint, which is no place to rebuild the tree being
            // painted: the next turn of the loop is, as for `web-view`.
            current.after(Duration.ZERO, () -> {
                if (isMounted()) {
                    setState(() -> reason = why);
                }
            });
        }

        @Override
        protected void dispose() {
            release();
        }

        private void release() {
            var current = layer;
            layer = null;
            if (current != null) {
                current.close();
            }
        }
    }
}
