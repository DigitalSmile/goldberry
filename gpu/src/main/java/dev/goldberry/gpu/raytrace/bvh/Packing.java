package dev.goldberry.gpu.raytrace.bvh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/// Words into little-endian buffers, the order every GPU the toolkit runs on
/// reads a storage buffer in.
final class Packing {

    private Packing() {}

    /// `words`, four bytes each; a buffer of one zero word when there are none,
    /// since a GPU buffer has a byte at least.
    static ByteBuffer words(int[] words) {
        var buffer =
                ByteBuffer.allocate(Math.max(1, words.length) * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asIntBuffer().put(words);
        return buffer;
    }

    /// `floats`, four bytes each; one zero float when there are none.
    static ByteBuffer floats(float[] floats) {
        var buffer =
                ByteBuffer.allocate(Math.max(1, floats.length) * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(floats);
        return buffer;
    }
}
