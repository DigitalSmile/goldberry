package io.github.digitalsmile.goldberry.natives.blend2d;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/// A Java enum standing in for one of Blend2D's C enums.
///
/// Blend2D declares its enums as `uint32_t` explicitly (`BL_DEFINE_ENUM`), so
/// the width is not in question the way a plain C enum's would be. The *values*
/// still are: several of these enumerate positionally, so inserting a constant
/// upstream shifts everything after it — which is one more thing a pinned commit
/// SHA (ADR-0030) makes a decision rather than an accident.
///
/// [#all()] feeds every constant to the layout verifier, which compares it
/// against what the C compiler computed for the library that is actually loaded.
/// The interface is sealed so the permitted list and [#all()] sit together.
public sealed interface BlendEnum
        permits BlendCompOp, BlendDataAccess, BlendFormat, BlendGlyphPlacementType,
                BlendResultCode, BlendRuntimeInfoType, BlendStrokeCap, BlendStrokeJoin,
                BlendTransformOp {

    /// The value Blend2D's header gives this constant.
    int nativeValue();

    /// The enumerator's name in C, which is the name the shim reports it under.
    String nativeName();

    /// Every Blend2D constant the bindings hard-code, for the layout verifier.
    static List<BlendEnum> all() {
        return Stream.<BlendEnum[]>of(
                        BlendCompOp.values(),
                        BlendDataAccess.values(),
                        BlendFormat.values(),
                        BlendGlyphPlacementType.values(),
                        BlendResultCode.values(),
                        BlendRuntimeInfoType.values(),
                        BlendStrokeCap.values(),
                        BlendStrokeJoin.values(),
                        BlendTransformOp.values())
                .flatMap(Arrays::stream)
                .toList();
    }
}
