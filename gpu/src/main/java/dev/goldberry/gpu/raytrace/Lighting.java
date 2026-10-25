package dev.goldberry.gpu.raytrace;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.OptionalInt;

/// How each pixel of a picture is lit, as [RayTracer#traceLighting] traced it:
/// two planes, rows from the top with no padding, each laid out for an upload
/// into a texture of the format it names.
///
/// [#shading()] is four bytes a pixel, for an `R8G8B8A8_UNORM` texture:
///
/// | Byte | Channel | What |
/// |------|---------|------|
/// | 0 | red | 0xFF where the light reaches the hit, 0 in shadow |
/// | 1 | green | how open the hit is: the share of its occlusion rays that escaped |
/// | 2 | blue | how lit the surface its reflection sees is: the cosine to the light, 0 in shadow |
/// | 3 | alpha | 0xFF where the primary ray hit anything |
///
/// [#reflectedIds()] is a float a pixel, for an `R32_FLOAT` texture: the id of
/// the instance the reflection ray hit, or -1 where it hit nothing or none was
/// traced. A shader reads which surface it sees, and its colour, from the id.
///
/// A pixel whose primary ray hit nothing is lit, open, sees nothing, and has
/// alpha 0.
public final class Lighting {

    /// Bytes a pixel, both planes.
    public static final int BYTES_PER_PIXEL = 8;

    /// The reflected id of a pixel whose reflection hit nothing.
    public static final float NO_REFLECTION = -1f;

    private final int width;
    private final int height;
    private final ByteBuffer planes;

    private Lighting(int width, int height, ByteBuffer planes) {
        this.width = width;
        this.height = height;
        this.planes = planes;
    }

    /// The lighting of a `width` by `height` picture in `planes`, the shading
    /// then the reflected ids, which become this object's.
    ///
    /// @throws IllegalArgumentException when `planes` is not the picture's size
    public static Lighting of(int width, int height, ByteBuffer planes) {
        Objects.requireNonNull(planes, "planes");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("a picture of " + width + "×" + height);
        }
        var expected = (long) width * height * BYTES_PER_PIXEL;
        if (planes.remaining() != expected) {
            throw new IllegalArgumentException(
                    width + "×" + height + " of lighting is " + expected + " bytes, not " + planes.remaining());
        }
        return new Lighting(width, height, planes.slice().order(ByteOrder.LITTLE_ENDIAN));
    }

    /// The picture's width.
    public int width() {
        return width;
    }

    /// The picture's height.
    public int height() {
        return height;
    }

    private int shading(int x, int y, int channel) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);
        return planes.get((y * width + x) * 4 + channel) & 0xFF;
    }

    /// Whether the pixel's primary ray hit anything.
    public boolean isHit(int x, int y) {
        return shading(x, y, 3) != 0;
    }

    /// Whether the light reaches what the pixel's ray hit, or it hit nothing.
    public boolean isLit(int x, int y) {
        return shading(x, y, 0) != 0;
    }

    /// How open the pixel's hit is, 0 (every occlusion ray blocked) to 1.
    public float openness(int x, int y) {
        return shading(x, y, 1) / 255f;
    }

    /// How lit the surface the pixel's reflection sees is, 0 to 1.
    public float reflectedLight(int x, int y) {
        return shading(x, y, 2) / 255f;
    }

    /// The id of the instance the pixel's reflection sees, if any.
    public OptionalInt reflectedId(int x, int y) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);
        var id = planes.getFloat(width * height * 4 + (y * width + x) * Float.BYTES);
        return id < 0 ? OptionalInt.empty() : OptionalInt.of((int) id);
    }

    /// How many pixels are in shadow.
    public int shadowedCount() {
        var count = 0;
        for (var i = 0; i < width * height; i++) {
            if (planes.get(i * 4) == 0) {
                count++;
            }
        }
        return count;
    }

    /// The shading plane, four bytes a pixel, for an `R8G8B8A8_UNORM`
    /// texture: read-only, from the start.
    public ByteBuffer shading() {
        return planes.slice(0, width * height * 4).asReadOnlyBuffer();
    }

    /// The reflected ids, a little-endian float a pixel, for an `R32_FLOAT`
    /// texture: read-only, from the start.
    public ByteBuffer reflectedIds() {
        // A view's order is big-endian whatever its source's, so it is set last.
        return planes.slice(width * height * 4, width * height * Float.BYTES)
                .asReadOnlyBuffer()
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public String toString() {
        return "Lighting[" + width + "×" + height + ", " + shadowedCount() + " in shadow]";
    }
}
