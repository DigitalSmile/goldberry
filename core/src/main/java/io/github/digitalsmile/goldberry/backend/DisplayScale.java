package io.github.digitalsmile.goldberry.backend;

/// A display's scale factor, and the only sanctioned way to convert between
/// logical and physical pixels.
///
/// Fractional scales are the normal case, not an edge case: 125% and 150% are
/// what Windows laptops and most Linux desktops ship with. `docs/ARCHITECTURE.md`
/// §4 requires them to be day-1 correct rather than retrofitted, which in
/// practice means one rounding rule, applied in one place, that everything else
/// is forbidden from reimplementing.
///
/// The rule: **physical = round(logical × factor)**, half away from zero. Sizes
/// and positions both. Rounding at the boundary rather than accumulating
/// fractions is what keeps a 1-pixel border 1 physical pixel wide at 150% instead
/// of a 1.5-pixel blur, and what stops two adjacent widgets from disagreeing
/// about where they meet.
public record DisplayScale(float factor) {

    /// The unscaled case — one logical pixel is one physical pixel.
    public static final DisplayScale ONE = new DisplayScale(1f);

    public DisplayScale {
        if (!Float.isFinite(factor)) {
            throw new IllegalArgumentException("scale factor must be finite, not " + factor);
        }
        if (factor <= 0f) {
            throw new IllegalArgumentException("scale factor must be positive, not " + factor);
        }
    }

    /// Converts a logical size to the physical pixels that back it.
    public PhysicalSize toPhysical(LogicalSize size) {
        return new PhysicalSize(toPhysical(size.width()), toPhysical(size.height()));
    }

    /// Converts one logical measurement to physical pixels.
    public int toPhysical(float logical) {
        var scaled = logical * factor;
        // Math.round clamps silently: a finite-but-huge float becomes
        // Integer.MAX_VALUE, and a frame buffer request for two billion pixels
        // fails somewhere far less informative than here.
        if (!Float.isFinite(scaled) || scaled > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    logical + " logical px at " + factor + "x does not fit in a pixel count");
        }
        return Math.round(scaled);
    }

    /// Converts physical pixels back to logical space.
    ///
    /// Not the inverse of [#toPhysical(float)] — rounding has already discarded
    /// information, so `toLogical(toPhysical(x))` is near `x`, not equal to it.
    /// Layout works in logical px and converts once, at the boundary, precisely
    /// to avoid needing this in the other direction.
    public float toLogical(int physical) {
        return physical / factor;
    }

    /// Converts a physical size back to logical space.
    public LogicalSize toLogical(PhysicalSize size) {
        return new LogicalSize(toLogical(size.width()), toLogical(size.height()));
    }

    /// Whether this is an integer scale, where logical and physical grids align
    /// exactly. Some rendering shortcuts are only valid here.
    public boolean isIntegral() {
        return factor == Math.rint(factor);
    }

    @Override
    public String toString() {
        return Math.round(factor * 100) + "%";
    }
}
