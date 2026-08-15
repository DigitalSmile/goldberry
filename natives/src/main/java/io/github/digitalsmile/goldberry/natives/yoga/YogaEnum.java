package io.github.digitalsmile.goldberry.natives.yoga;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/// A Java enum standing in for one of Yoga's C enums.
///
/// Yoga declares its enums with a plain `enum` (see `YG_ENUM_BEGIN` in
/// `YGMacros.h`), so every one of them crosses the boundary as an `int`. The
/// Java constants carry their C value explicitly rather than relying on ordinal
/// order, because reordering a Java enum is an ordinary refactoring and would
/// otherwise silently change what Yoga is told.
///
/// The value is not taken on trust: [#all()] feeds every constant to the layout
/// verifier, which compares it against what the C compiler computed for the
/// library that is actually loaded. A Java constant that drifts from Yoga's
/// header fails a test rather than producing a plausible-looking wrong layout.
///
/// The interface is sealed so that the permitted list and [#all()] sit together
/// — an enum added to one and not the other is visible on the same screen.
public sealed interface YogaEnum
        permits Align, Direction, Display, Edge, FlexDirection, Gutter, Justify,
                MeasureMode, Overflow, PositionType, Wrap {

    /// The value Yoga's header gives this constant.
    int nativeValue();

    /// The enumerator's name in C, which is the name the shim reports it under.
    String nativeName();

    /// Every Yoga constant the bindings hard-code, for the layout verifier.
    static List<YogaEnum> all() {
        return Stream.<YogaEnum[]>of(
                        Align.values(),
                        Direction.values(),
                        Display.values(),
                        Edge.values(),
                        FlexDirection.values(),
                        Gutter.values(),
                        Justify.values(),
                        MeasureMode.values(),
                        Overflow.values(),
                        PositionType.values(),
                        Wrap.values())
                .flatMap(Arrays::stream)
                .toList();
    }
}
