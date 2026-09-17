package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// One of an image's rasters, and the display scale it was drawn for — §1's
/// "DPI-aware (picks raster scale by physical pixels)".
///
/// A `logo@2x.png` beside `logo.png` is two variants of one picture: the second
/// has twice the pixels and the **same** natural size, so on a 200% display it is
/// drawn one image pixel per device pixel and on a 100% display the first is.
/// That is HTML's `srcset` with `x` descriptors, and [#parse] reads that syntax.
///
/// @param scale  the display scale this raster is for; positive
/// @param source where it comes from
public record Variant(double scale, ImageSource source) {

    public Variant {
        if (!(scale > 0) || !Double.isFinite(scale)) {
            throw new IllegalArgumentException("a variant's scale is a positive number, not " + scale);
        }
        Objects.requireNonNull(source, "source");
    }

    /// The variant to draw at `displayScale`: the smallest one at least that
    /// large, so no pixel is invented, or the largest there is when none is.
    public static Variant pick(List<Variant> variants, double displayScale) {
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("an image has at least one variant");
        }
        var sorted = variants.stream()
                .sorted(Comparator.comparingDouble(Variant::scale))
                .toList();
        for (var variant : sorted) {
            if (variant.scale() >= displayScale - 1e-6) {
                return variant;
            }
        }
        return sorted.getLast();
    }

    /// Reads a `srcset`: `"logo.png 1x, logo@2x.png 2x"`. A candidate with no
    /// descriptor is `1x`.
    ///
    /// @throws IllegalArgumentException for a descriptor that is not `Nx`, or two
    ///         candidates for one scale
    @SuppressWarnings("StringSplitter") // empty candidates are skipped below
    public static List<Variant> parse(String srcset, ClassLoader loader) {
        var variants = new ArrayList<Variant>();
        for (var candidate : srcset.split(",")) {
            var parts = candidate.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) {
                continue;
            }
            var scale = 1.0;
            if (parts.length > 1) {
                var descriptor = parts[1].toLowerCase(Locale.ROOT);
                if (parts.length > 2 || !descriptor.endsWith("x")) {
                    throw new IllegalArgumentException(
                            "a srcset candidate is a path and an Nx scale, and \"" + candidate.trim() + "\" is not");
                }
                try {
                    scale = Double.parseDouble(descriptor.substring(0, descriptor.length() - 1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("\"" + parts[1] + "\" is not a scale like 2x", e);
                }
            }
            var chosen = scale;
            if (variants.stream().anyMatch(variant -> variant.scale() == chosen)) {
                throw new IllegalArgumentException("a srcset names two images for " + parts[1]);
            }
            variants.add(new Variant(scale, ImageSource.parse(parts[0], loader)));
        }
        return List.copyOf(variants);
    }
}
