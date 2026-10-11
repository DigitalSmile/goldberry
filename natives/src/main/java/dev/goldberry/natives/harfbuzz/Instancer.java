package dev.goldberry.natives.harfbuzz;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// A variable font pinned to one point of its design space: `wght` at 450,
/// `wdth` at 87.5, every other axis at its default, as the bytes of a static
/// font.
///
/// What a variable face has to become before it is drawn. Blend2D reads a
/// variable face's outlines at their default instance whatever it is told, and
/// HarfBuzz would shape the varied one, so a weight between the masters is
/// made into a face of its own and both libraries are handed that.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class Instancer {

    private Instancer() {}

    /// One axis and where it is pinned.
    ///
    /// @param tag   the four-letter axis tag, `wght` or `wdth`
    /// @param value the position, in the axis's own units: 400 for a regular
    ///              weight, 100 for a normal width
    public record Axis(String tag, float value) {
        public Axis {
            Objects.requireNonNull(tag, "tag");
            if (tag.length() != 4 || !tag.chars().allMatch(c -> c >= 0x20 && c <= 0x7E)) {
                throw new IllegalArgumentException(
                        "an axis tag is four printable ASCII characters, not \"" + tag + "\"");
            }
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("an axis position is finite, not " + value);
            }
        }

        /// The tag as the `uint32_t` an OpenType table stores it as.
        int packed() {
            return (tag.charAt(0) << 24) | (tag.charAt(1) << 16) | (tag.charAt(2) << 8) | tag.charAt(3);
        }
    }

    /// `font` with every axis pinned: those in `axes` where they say, the rest
    /// at their defaults. Empty when the bytes cannot be instanced, which is
    /// what a static font, or one the instancer refuses, gives; the caller then
    /// keeps the face it had.
    ///
    /// @param font the variable font's bytes
    /// @param axes where to pin, by tag; an axis the face does not have is
    ///             ignored
    public static Optional<byte[]> instance(byte[] font, List<Axis> axes) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(axes, "axes");
        if (font.length == 0) {
            throw new IllegalArgumentException("a font file with no bytes in it is not a font");
        }
        var harfbuzz = HarfBuzz.get();
        try (var arena = Arena.ofConfined()) {
            var data = arena.allocate(ValueLayout.JAVA_BYTE, font.length);
            MemorySegment.copy(font, 0, data, ValueLayout.JAVA_BYTE, 0, font.length);
            var tags = arena.allocate(ValueLayout.JAVA_INT, Math.max(1, axes.size()));
            var values = arena.allocate(ValueLayout.JAVA_FLOAT, Math.max(1, axes.size()));
            for (var i = 0; i < axes.size(); i++) {
                tags.setAtIndex(ValueLayout.JAVA_INT, i, axes.get(i).packed());
                values.setAtIndex(ValueLayout.JAVA_FLOAT, i, axes.get(i).value());
            }
            var blob = harfbuzz.blobCreate(data, font.length);
            try {
                var instanced = harfbuzz.instance(blob, tags, values, axes.size());
                if (instanced.equals(MemorySegment.NULL)) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(harfbuzz.blobBytes(instanced, arena));
                } finally {
                    harfbuzz.blobDestroy(instanced);
                }
            } finally {
                harfbuzz.blobDestroy(blob);
            }
        }
    }
}
