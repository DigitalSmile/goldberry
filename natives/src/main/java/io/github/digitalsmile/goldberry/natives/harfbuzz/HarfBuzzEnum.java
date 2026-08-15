package io.github.digitalsmile.goldberry.natives.harfbuzz;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/// A Java enum standing in for one of HarfBuzz's C enums.
///
/// HarfBuzz numbers several of its enums with deliberate gaps — `HB_DIRECTION_LTR`
/// is 4, not 1, so that the low bits carry meaning — which is precisely why the
/// values are declared rather than counted from an ordinal.
///
/// [#all()] feeds every constant to the layout verifier. The interface is sealed
/// so the permitted list and [#all()] sit together.
public sealed interface HarfBuzzEnum permits MemoryMode, TextDirection {

    /// The value HarfBuzz's header gives this constant.
    int nativeValue();

    /// The enumerator's name in C, which is the name the shim reports it under.
    String nativeName();

    /// Every HarfBuzz constant the bindings hard-code, for the layout verifier.
    static List<HarfBuzzEnum> all() {
        return Stream.<HarfBuzzEnum[]>of(MemoryMode.values(), TextDirection.values())
                .flatMap(Arrays::stream)
                .toList();
    }
}
