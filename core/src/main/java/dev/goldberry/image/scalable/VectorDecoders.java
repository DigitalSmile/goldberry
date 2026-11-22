package dev.goldberry.image.scalable;

import java.util.Optional;
import java.util.ServiceLoader;

import org.jspecify.annotations.Nullable;

import dev.goldberry.image.spi.VectorDecoder;

/// The [VectorDecoder] in use: the first on the module path, looked for once.
public final class VectorDecoders {

    /// What an SVG without a decoder is told to add.
    public static final String ARTIFACT = "dev.goldberry:goldberry-vector";

    /// The answer, once looked for: a decoder, or none.
    private record Found(@Nullable VectorDecoder decoder) {}

    private static volatile @Nullable Found found;

    private VectorDecoders() {}

    /// The decoder, or empty when no module provides one.
    public static Optional<VectorDecoder> find() {
        var answer = found;
        if (answer == null) {
            answer = new Found(
                    ServiceLoader.load(VectorDecoder.class).findFirst().orElse(null));
            found = answer;
        }
        return Optional.ofNullable(answer.decoder());
    }

    /// Puts `decoder` in front of the service loader's answer, for a test; null
    /// looks again.
    public static void use(@Nullable VectorDecoder decoder) {
        found = decoder == null ? null : new Found(decoder);
    }

    /// Answers that no module provides one, for a test of the message an
    /// application without `goldberry-vector` sees.
    public static void useNone() {
        found = new Found(null);
    }
}
