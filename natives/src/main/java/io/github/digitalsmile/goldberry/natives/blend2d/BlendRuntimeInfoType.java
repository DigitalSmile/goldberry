package io.github.digitalsmile.goldberry.natives.blend2d;

/// Which block of information `bl_runtime_query_info` should fill in —
/// `BLRuntimeInfoType`.
///
/// The type selects the struct the out-parameter must be, and the sizes differ.
/// Asking for [#SYSTEM] with a [#BUILD]-sized buffer is a write past the end,
/// which is why the one call that takes this pairs the two in a single place
/// rather than accepting both from a caller.
public enum BlendRuntimeInfoType implements BlendEnum {

    /// `BLRuntimeBuildInfo` — version and compiler. The only one bound.
    BUILD(0, "BL_RUNTIME_INFO_TYPE_BUILD"),

    /// `BLRuntimeSystemInfo` — CPU architecture, features, core count.
    SYSTEM(1, "BL_RUNTIME_INFO_TYPE_SYSTEM"),

    /// `BLRuntimeResourceInfo` — Blend2D's own memory consumption.
    RESOURCE(2, "BL_RUNTIME_INFO_TYPE_RESOURCE");

    private final int nativeValue;
    private final String nativeName;

    BlendRuntimeInfoType(int nativeValue, String nativeName) {
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
