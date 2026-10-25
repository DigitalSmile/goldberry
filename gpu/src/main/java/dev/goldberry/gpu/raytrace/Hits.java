package dev.goldberry.gpu.raytrace;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;

/// What a [RayTracer] found, a record a pixel, rows from the top.
///
/// Each record is [#RECORD_BYTES] bytes, little-endian, in this order, which
/// is also how an application's own shader reads [#records()] uploaded into a
/// storage buffer:
///
/// | Offset | Type | Field |
/// |--------|------|-------|
/// | 0 | `uint` | the instance's id, or [#MISS] |
/// | 4 | `uint` | the triangle's index in its mesh |
/// | 8 | `float` | the distance along the ray |
/// | 12 | `float` | barycentric `u` |
/// | 16 | `float` | barycentric `v` |
/// | 20 | `uint` | 1 when lit, 0 in shadow |
///
/// The pictures made from it — [#shadowMask()], [#distances(float)] — are laid
/// out for an upload into a texture of the format each names.
public final class Hits {

    /// The size of one pixel's record.
    public static final int RECORD_BYTES = 24;

    /// The instance id of a pixel whose ray hit nothing.
    public static final int MISS = 0xFFFF_FFFF;

    private final int width;
    private final int height;
    private final ByteBuffer records;

    private Hits(int width, int height, ByteBuffer records) {
        this.width = width;
        this.height = height;
        this.records = records;
    }

    /// The hits of a `width` by `height` picture in `records`, laid out as
    /// above, which become this object's.
    ///
    /// @throws IllegalArgumentException when `records` is not the picture's size
    public static Hits of(int width, int height, ByteBuffer records) {
        Objects.requireNonNull(records, "records");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("a picture of " + width + "×" + height);
        }
        var expected = (long) width * height * RECORD_BYTES;
        if (records.remaining() != expected) {
            throw new IllegalArgumentException(
                    width + "×" + height + " hits are " + expected + " bytes, not " + records.remaining());
        }
        return new Hits(width, height, records.slice().order(ByteOrder.LITTLE_ENDIAN));
    }

    /// The picture's width.
    public int width() {
        return width;
    }

    /// The picture's height.
    public int height() {
        return height;
    }

    /// Whether pixel `x`, `y`'s ray hit anything.
    public boolean isHit(int x, int y) {
        return instanceId(x, y) != MISS;
    }

    /// The id of the instance pixel `x`, `y`'s ray hit, or [#MISS].
    public int instanceId(int x, int y) {
        return records.getInt(at(x, y));
    }

    /// The index of the triangle it hit in its mesh; meaningless on a miss.
    public int primitiveId(int x, int y) {
        return records.getInt(at(x, y) + 4);
    }

    /// How far along the ray it hit; meaningless on a miss.
    public float distance(int x, int y) {
        return records.getFloat(at(x, y) + 8);
    }

    /// Whether the light reaches the point it hit. True on a miss.
    public boolean isLit(int x, int y) {
        return records.getInt(at(x, y) + 20) != 0;
    }

    /// Pixel `x`, `y`'s hit, or empty where its ray hit nothing.
    public Optional<Hit> hit(int x, int y) {
        var at = at(x, y);
        var instance = records.getInt(at);
        if (instance == MISS) {
            return Optional.empty();
        }
        return Optional.of(new Hit(
                instance,
                records.getInt(at + 4),
                records.getFloat(at + 8),
                records.getFloat(at + 12),
                records.getFloat(at + 16),
                records.getInt(at + 20) != 0));
    }

    /// How many pixels' rays hit something.
    public int hitCount() {
        var count = 0;
        for (var at = 0; at < records.limit(); at += RECORD_BYTES) {
            if (records.getInt(at) != MISS) {
                count++;
            }
        }
        return count;
    }

    /// The records as they are laid out above: a read-only view.
    public ByteBuffer records() {
        return records.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    }

    /// Where the light reaches, made from the records: [ShadowMask#LIT] where
    /// it does, or where the ray hit nothing, and [ShadowMask#SHADOWED] in
    /// shadow. A trace that wants the mask alone asks
    /// [RayTracer#traceShadows], which packs it on the GPU.
    public ShadowMask shadowMask() {
        var mask = ByteBuffer.allocate(width * height);
        for (var i = 0; i < width * height; i++) {
            mask.put(i, records.getInt(i * RECORD_BYTES + 20) != 0 ? ShadowMask.LIT : ShadowMask.SHADOWED);
        }
        return ShadowMask.of(width, height, mask);
    }

    /// An `R32_FLOAT` picture of each hit's distance, and of `missDistance`
    /// where the ray hit nothing. A new little-endian buffer, four bytes a
    /// pixel.
    public ByteBuffer distances(float missDistance) {
        var distances = ByteBuffer.allocate(width * height * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (var i = 0; i < width * height; i++) {
            var at = i * RECORD_BYTES;
            distances.putFloat(i * Float.BYTES, records.getInt(at) == MISS ? missDistance : records.getFloat(at + 8));
        }
        return distances;
    }

    private int at(int x, int y) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);
        return (y * width + x) * RECORD_BYTES;
    }

    @Override
    public String toString() {
        return "Hits[" + width + "×" + height + ", " + hitCount() + " hit]";
    }
}
