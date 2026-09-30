package io.github.digitalsmile.goldberry.media.view.gpu;

import io.github.digitalsmile.goldberry.gpu.render.YuvConversion;
import io.github.digitalsmile.goldberry.gpu.render.YuvLayout;
import io.github.digitalsmile.goldberry.gpu.render.YuvMatrix;
import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.picture.VideoPlanes;

/// What the GPU's video layer draws for a picture of planes at 1:1, computed in
/// Java: [YuvConversion] over chroma sampled as a linear sampler samples it,
/// centred (ADR-0484).
///
/// At 1:1 a luma texel is read at its centre, exactly. A chroma plane is half
/// the size, so the luma pixel's centre falls, in chroma texels, at `x / 2 -
/// 1/4` across and `y / 2 - 1/4` down: each pixel reads three quarters of the
/// nearer chroma sample and a quarter of the other, both ways, clamped at the
/// edges as the sampler clamps. It is what swscale's bilinear conversion with
/// full horizontal chroma interpolation computes too, which is how the CPU's
/// siting was found.
final class PlanesReference {

    private PlanesReference() {}

    /// The picture as `0xAARRGGBB`, row by row.
    static int[] argb(VideoPlanes planes) {
        var layout = YuvLayout.valueOf(planes.format().name());
        var conversion =
                new YuvConversion(layout, YuvMatrix.valueOf(planes.matrix().name()), planes.fullRange());
        var width = planes.width();
        var height = planes.height();
        var chromaWidth = (width + 1) / 2;
        var chromaHeight = (height + 1) / 2;
        var out = new int[width * height];
        for (var y = 0; y < height; y++) {
            var cy = y / 2.0 - 0.25;
            var row0 = clamp((int) Math.floor(cy), chromaHeight);
            var row1 = clamp((int) Math.floor(cy) + 1, chromaHeight);
            var fy = cy - Math.floor(cy);
            for (var x = 0; x < width; x++) {
                var cx = x / 2.0 - 0.25;
                var column0 = clamp((int) Math.floor(cx), chromaWidth);
                var column1 = clamp((int) Math.floor(cx) + 1, chromaWidth);
                var fx = cx - Math.floor(cx);
                var cb = bilinear(planes, 0, column0, column1, row0, row1, fx, fy);
                var cr = bilinear(planes, 1, column0, column1, row0, row1, fx, fy);
                var rgb = conversion.toRgbBytes(code(planes, 0, x, y), cb, cr);
                out[y * width + x] = 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
            }
        }
        return out;
    }

    private static double bilinear(
            VideoPlanes planes, int component, int x0, int x1, int y0, int y1, double fx, double fy) {
        var top = chroma(planes, component, x0, y0) * (1 - fx) + chroma(planes, component, x1, y0) * fx;
        var bottom = chroma(planes, component, x0, y1) * (1 - fx) + chroma(planes, component, x1, y1) * fx;
        return top * (1 - fy) + bottom * fy;
    }

    /// Cb (`component` 0) or Cr (1) at chroma texel (`x`, `y`), as a code.
    private static int chroma(VideoPlanes planes, int component, int x, int y) {
        return planes.format().planes() == 2
                ? code(planes, 1, x * 2 + component, y)
                : code(planes, 1 + component, x, y);
    }

    /// The code a stored sample holds: P010 keeps its 10 bits high.
    private static int code(VideoPlanes planes, int plane, int column, int row) {
        var sample = planes.sample(plane, column, row);
        return planes.format() == PixelFormat.P010 ? sample >>> 6 : sample;
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(size - 1, index));
    }
}
