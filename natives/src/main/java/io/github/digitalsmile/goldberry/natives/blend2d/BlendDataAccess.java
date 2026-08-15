package io.github.digitalsmile.goldberry.natives.blend2d;

/// What Blend2D may do with memory it was handed — `BLDataAccessFlags`.
///
/// A bit set rather than a sequence, so the values do not shift if Blend2D adds
/// one. Goldberry only ever passes [#READ_WRITE]: the whole point of lending
/// Blend2D the compositor's buffer is that it writes into it.
public enum BlendDataAccess implements BlendEnum {

    NONE(0x00, "BL_DATA_ACCESS_NO_FLAGS"),

    READ(0x01, "BL_DATA_ACCESS_READ"),

    WRITE(0x02, "BL_DATA_ACCESS_WRITE"),

    READ_WRITE(0x03, "BL_DATA_ACCESS_RW");

    private final int nativeValue;
    private final String nativeName;

    BlendDataAccess(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }
}
