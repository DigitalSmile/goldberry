package dev.goldberry.render.dragout;

import java.util.Objects;
import java.util.Optional;

import dev.goldberry.input.dragout.DragPicture;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// The picture a drag out hands the platform when its offer brings none: the
/// source as the window last showed it, faded as the in-app ghost is.
///
/// Cut from the window's last presented frame rather than painted again, so
/// it costs one copy of the source's rectangle and no layout. Whatever was
/// drawn under the source's rounded corners comes with it; an offer that
/// minds gives its own picture.
public final class DragPictures {

    /// How opaque the picture is: the in-app ghost's opacity.
    public static final double OPACITY = 0.7;

    private DragPictures() {}

    /// `from` cut out of `frame`, faded to [#OPACITY], with the hotspot at
    /// `grab`; empty when the rectangle is not on the frame at all.
    ///
    /// @param frame the window's last frame, premultiplied BGRA
    /// @param from  the source's painted rectangle, in the window's logical pixels
    /// @param grab  where in `from` the pointer took hold, in logical pixels
    /// @param scale the window's scale
    public static Optional<DragPicture> ghost(
            PixelBuffer frame, LogicalRect from, LogicalPoint grab, DisplayScale scale) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(grab, "grab");
        Objects.requireNonNull(scale, "scale");
        if (frame.format() != PixelFormat.BGRA32_PREMULTIPLIED) {
            return Optional.empty();
        }
        var left = Math.max(0, scale.toPhysical(from.left()));
        var top = Math.max(0, scale.toPhysical(from.top()));
        var right = Math.min(frame.size().width(), scale.toPhysical(from.right()));
        var bottom = Math.min(frame.size().height(), scale.toPhysical(from.bottom()));
        var width = right - left;
        var height = bottom - top;
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        var picture = PixelBuffer.allocate(new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED);
        var source = frame.pixels();
        var target = picture.pixels();
        for (var row = 0; row < height; row++) {
            var in = (top + row) * frame.stride() + left * 4;
            var out = row * picture.stride();
            for (var column = 0; column < width * 4; column++) {
                var value = Byte.toUnsignedInt(source.get(in + column));
                target.put(out + column, (byte) Math.round(value * OPACITY));
            }
        }
        var hotspotX = clamp(scale.toPhysical(from.left() + grab.x()) - left, width);
        var hotspotY = clamp(scale.toPhysical(from.top() + grab.y()) - top, height);
        return Optional.of(new DragPicture(picture, hotspotX, hotspotY));
    }

    private static int clamp(int value, int extent) {
        return Math.clamp(value, 0, extent - 1);
    }
}
