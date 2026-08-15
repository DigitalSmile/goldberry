package io.github.digitalsmile.goldberry.natives.blend2d;

/// The `BLResult` values Goldberry names.
///
/// Blend2D reports every failure as a numeric result and exports **no**
/// result-to-string function — `blend2d-debug.h` is header-only, so there is no
/// symbol to bind. Without this, a failure would surface as `0x00010002` and
/// nothing else.
///
/// Not every code Blend2D defines is here, and that is deliberate: a name in
/// this enum is a constant the layout probe checks against the compiled library,
/// so naming codes nobody can trigger would be registry weight for no reading.
/// Anything unnamed still reports its hex value, which is enough to look up.
public enum BlendResultCode implements BlendEnum {

    /// Not a failure. Blend2D's `BL_SUCCESS` is zero, the only non-error value.
    SUCCESS(0x00000000, "BL_SUCCESS"),

    OUT_OF_MEMORY(0x00010000, "BL_ERROR_OUT_OF_MEMORY"),

    /// The usual answer to a bad argument — a zero-sized image, a stride that
    /// cannot hold a row.
    INVALID_VALUE(0x00010001, "BL_ERROR_INVALID_VALUE"),

    INVALID_STATE(0x00010002, "BL_ERROR_INVALID_STATE"),

    /// Reported when an object is used before it was initialised, which for
    /// these bindings would mean a wrapper escaped its constructor.
    NOT_INITIALIZED(0x00010006, "BL_ERROR_NOT_INITIALIZED"),

    NOT_IMPLEMENTED(0x00010007, "BL_ERROR_NOT_IMPLEMENTED");

    private final int nativeValue;
    private final String nativeName;

    BlendResultCode(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    /// The `BLResult` value this maps to.
    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }

    /// The name for a result, or null when Blend2D defines a code Goldberry has
    /// not named.
    static BlendResultCode of(int result) {
        for (var code : values()) {
            if (code.nativeValue == result) {
                return code;
            }
        }
        return null;
    }
}
