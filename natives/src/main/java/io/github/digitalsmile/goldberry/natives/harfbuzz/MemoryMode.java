package io.github.digitalsmile.goldberry.natives.harfbuzz;

/// What HarfBuzz may do with bytes it is handed — `hb_memory_mode_t`.
///
/// Only the two safe values are bound. The writable modes let HarfBuzz modify
/// or take ownership of the caller's memory, which is not something to offer a
/// font loaded from a Java array.
public enum MemoryMode implements HarfBuzzEnum {

    /// Copy the bytes immediately. The caller's array can then be collected,
    /// which is what makes it safe to build a face from a `byte[]`.
    DUPLICATE(0, "HB_MEMORY_MODE_DUPLICATE"),

    /// Do not copy, and never write. The bytes must outlive the face.
    READONLY(1, "HB_MEMORY_MODE_READONLY");

    private final int nativeValue;
    private final String nativeName;

    MemoryMode(int nativeValue, String nativeName) {
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
