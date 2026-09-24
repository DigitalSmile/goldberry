package io.github.digitalsmile.goldberry.gpu;

import java.util.Objects;

/// A pipeline's depth test.
///
/// @param format  the depth target's format; a pipeline with this test draws
///                only in a render pass with a [DepthTarget] of that format
/// @param compare how a fragment's depth is compared with the target's
/// @param write   whether a fragment that passes writes its depth
public record DepthTest(TextureFormat format, CompareOp compare, boolean write) {

    /// Checks the format is a depth format.
    public DepthTest {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(compare, "compare");
        if (!format.isDepth()) {
            throw new IllegalArgumentException(format + " is not a depth format");
        }
    }

    /// Nearer fragments pass and write their depth: the usual test, against a
    /// target cleared to 1.
    public static DepthTest less(TextureFormat format) {
        return new DepthTest(format, CompareOp.LESS, true);
    }
}
