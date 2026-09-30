package io.github.digitalsmile.goldberry.gpu.composite;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import io.github.digitalsmile.goldberry.gpu.GpuFrame;
import io.github.digitalsmile.goldberry.gpu.GpuLayer;
import io.github.digitalsmile.goldberry.gpu.GpuTexture;
import io.github.digitalsmile.goldberry.gpu.Load;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;

/// `docs/gpu-plan.md` phase 4's test layer: a solid colour with a square of
/// another on it, where [#moveTo] puts it. Written against the public API alone,
/// as an application's layer is, and drawn with no shader: a render pass that
/// clears, then an upload of the square.
///
/// Colours are opaque and their channels 0 or 255, so a pixel read back is
/// exactly the colour asked for, on any driver.
final class TestLayer implements GpuLayer {

    private final int background;
    private final int square;
    private double left = 0.25;
    private double top = 0.25;
    private int renders;

    /// A layer of `background` with a `square` half its height on it, both
    /// `0xAARRGGBB` with every channel 0 or 255.
    TestLayer(int background, int square) {
        this.background = background;
        this.square = square;
    }

    /// Puts the square's top left corner at `left` and `top`, as fractions of
    /// the layer's size, so the picture scales with the layer.
    TestLayer moveTo(double left, double top) {
        this.left = left;
        this.top = top;
        return this;
    }

    /// How many times it has been rendered.
    int renders() {
        return renders;
    }

    private boolean still;

    /// Says from now on that its picture has not changed, or that it has.
    TestLayer still(boolean value) {
        still = value;
        return this;
    }

    @Override
    public boolean needsRender() {
        return !still;
    }

    @Override
    public void render(GpuFrame frame, GpuTexture target) {
        renders++;
        frame.renderPass(
                target,
                Load.clear(channel(background, 16), channel(background, 8), channel(background, 0), 1),
                pass -> {});
        var width = target.width();
        var height = target.height();
        var side = Math.max(1, height / 2);
        var x = (int) Math.round(left * width);
        var y = (int) Math.round(top * height);
        var region = new PhysicalRect(x, y, Math.min(side, width - x), Math.min(side, height - y));
        if (region.width() <= 0 || region.height() <= 0) {
            return;
        }
        var pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (var row = region.y(); row < region.bottom(); row++) {
            for (var column = region.x(); column < region.right(); column++) {
                pixels.putInt((row * width + column) * 4, square);
            }
        }
        frame.copyPass(copy -> copy.upload(target, pixels, width * 4, List.of(region)));
    }

    private static float channel(int argb, int shift) {
        return ((argb >>> shift) & 0xFF) / 255f;
    }

    @Override
    public String toString() {
        return "TestLayer[" + Integer.toHexString(background) + " with " + Integer.toHexString(square) + "]";
    }
}
