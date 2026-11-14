package dev.goldberry.gpu.view;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.gpu.upscale.RenderScale;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The box a [Canvas3d] is: styled as `canvas3d`, and painted by placing its
/// layer.
///
/// What it paints is a [Canvas3dPainter], a record, because the render tree
/// compares painters by equality to decide what was damaged: the painter is
/// equal from frame to frame while the CPU frame has nothing new to show, so
/// its box is not repainted, and differs when it has -- at a new revision or
/// render scale, and every frame while it is continuous **and its pixels are
/// read back into the CPU frame**.
///
/// A composited canvas that draws every frame does not damage the box: the
/// compositor draws the layer from its own texture on every present and
/// re-renders it while it asks to be ([GpuLayer#needsRender]), so repainting
/// the box would only rasterise and upload the UI over it again, every frame,
/// at the size of the canvas. The frame's time reaches the layer as the tree
/// is built rather than through the painter for that reason.
record Canvas3dSurface(
        Canvas3dLayer layer,
        boolean continuous,
        long revision,
        RenderScale renderScale,
        Owner owner,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Semantics {

    /// The fill where there is no GPU to draw with, when the theme names none.
    static final int DEFAULT_UNAVAILABLE = 0xFF3B4252;

    /// The theme token for that fill.
    static final String UNAVAILABLE_TOKEN = "--gb-canvas3d-unavailable";

    /// What a surface reports to the state that made it.
    interface Owner {

        /// The frame's time for the renderer, in nanoseconds since the canvas
        /// was first drawn, from the window's clock now: `nowMillis` itself
        /// while `continuous`, and the time `revision` was first drawn
        /// otherwise, so an on-demand canvas's painter stays equal between
        /// revisions.
        long nanos(double nowMillis, boolean continuous, long revision);

        /// Whether the last paint drew the layer, and whether its frame had a
        /// GPU at all: what the notice says. Called from inside a paint.
        void shown(boolean drawn, boolean hasGpu);
    }

    @Override
    public String cssType() {
        return "canvas3d";
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
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of()
                .style(style)
                .painting(painter(
                        layer,
                        context.nowMillis(),
                        continuous,
                        revision,
                        renderScale,
                        context.color(UNAVAILABLE_TOKEN, DEFAULT_UNAVAILABLE),
                        owner));
    }

    /// This frame's painter, with the frame's time handed to the layer.
    ///
    /// The stamp is what makes two painters differ: the frame's own time while
    /// the canvas is continuous and its last paint was read back into the CPU
    /// frame, which then has to be repainted to show the new picture; and the
    /// revision otherwise, which differs only when a redraw is wanted. A
    /// composited canvas learns which it is from its first paint, and is
    /// repainted every frame until then.
    static Canvas3dPainter painter(
            Canvas3dLayer layer,
            double nowMillis,
            boolean continuous,
            long revision,
            RenderScale renderScale,
            int fallback,
            Owner owner) {
        layer.frame(owner.nanos(nowMillis, continuous, revision), continuous);
        var stamp = continuous && !layer.composited() ? Double.doubleToLongBits(nowMillis) : revision;
        return new Canvas3dPainter(layer, stamp, continuous, revision, renderScale, fallback, owner);
    }

    @Override
    public boolean isAnimating(ComputedStyle style, Context context) {
        return continuous;
    }

    @Override
    public Role role() {
        return Role.FIGURE;
    }

    @Override
    public @Nullable String accessibleName() {
        return null;
    }

    /// Places the layer over the whole box, or fills it where the frame cannot
    /// show one. Equal while there is nothing new to draw: see the class.
    record Canvas3dPainter(
            Canvas3dLayer layer,
            long stamp,
            boolean continuous,
            long revision,
            RenderScale renderScale,
            int fallback,
            Owner owner)
            implements Painter {

        @Override
        public void paint(Frame frame, LogicalSize size) {
            layer.scale(renderScale);
            layer.composited(frame.composesGpuLayers());
            var drawn = frame.gpuLayer(layer, 0, 0, size.width(), size.height());
            if (!drawn) {
                frame.fillRect(0, 0, size.width(), size.height(), fallback);
            }
            owner.shown(drawn, frame.hasGpu());
        }
    }
}
