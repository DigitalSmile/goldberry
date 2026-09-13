package io.github.digitalsmile.goldberry.natives.md4c.enums;

/// How a table cell's content is aligned — `MD_ALIGN`, set by the `:---:` row.
public enum CellAlign implements Md4cEnum {

    /// No colon on either side: the renderer's own default.
    DEFAULT(0, "MD_ALIGN_DEFAULT"),

    LEFT(1, "MD_ALIGN_LEFT"),

    CENTER(2, "MD_ALIGN_CENTER"),

    RIGHT(3, "MD_ALIGN_RIGHT");

    private final int nativeValue;
    private final String nativeName;

    CellAlign(int nativeValue, String nativeName) {
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

    /// The alignment md4c reported, by its C value.
    ///
    /// @throws IllegalArgumentException if no alignment has that value
    public static CellAlign of(int nativeValue) {
        for (var align : values()) {
            if (align.nativeValue == nativeValue) {
                return align;
            }
        }
        throw new IllegalArgumentException(
                "libgoldberry reported MD_ALIGN " + nativeValue + ", which these bindings do not know");
    }
}
