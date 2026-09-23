package io.github.digitalsmile.goldberry.natives.md4c.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/// A Java enum standing in for one of md4c's C enums.
///
/// The same contract [io.github.digitalsmile.goldberry.natives.yoga.style.YogaEnum]
/// has, for the same reason, and md4c has the strongest case of the three wrapped
/// libraries: it has inserted enumerators into the *middle* of these enums between
/// releases — `MD_TEXT_NULLCHAR` and `MD_SPAN_LATEXMATH` were both additions, and
/// 0.6.0 put `MD_SPAN_INS` in front of `MD_SPAN_DEL` — and
/// a stream decoded against a shifted value does not crash. It renders a heading
/// as a block quote.
///
/// So every constant carries md4c's own value, [#all()] hands the lot to the
/// layout verifier, and `goldberry_shim.c` reports what the C compiler computed
/// for the library actually loaded (ADR-0294).
///
/// The interface is sealed so that the permitted list and [#all()] sit together.
public sealed interface Md4cEnum permits BlockType, SpanType, TextType, CellAlign, MarkdownFlag {

    /// The value md4c's header gives this constant.
    int nativeValue();

    /// The enumerator's name in C, which is the name the shim reports it under.
    String nativeName();

    /// Every md4c constant the bindings hard-code, for the layout verifier.
    static List<Md4cEnum> all() {
        return Stream.<Md4cEnum[]>of(
                        BlockType.values(),
                        SpanType.values(),
                        TextType.values(),
                        CellAlign.values(),
                        MarkdownFlag.values())
                .flatMap(Arrays::stream)
                .toList();
    }
}
