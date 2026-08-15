package io.github.digitalsmile.goldberry.natives.layout;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.List;

/// The registry of hand-written struct layouts.
///
/// Every layout declared here is checked against the compiled library by
/// [LayoutVerifier]. That check is what makes hand-writing bindings defensible
/// (ADR-0010), so **a layout added to the bindings must be added here, and its C
/// type must be registered in `goldberry_shim.c`.** A layout in one place and
/// not the other is a test failure by design.
public final class Layouts {

    /// The canary.
    ///
    /// Deliberately awkward: a byte followed by an int forces three bytes of
    /// padding, and the pointer and double that follow have to land on their
    /// natural alignment. Modelling it correctly exercises everything a real
    /// struct layout needs, and it is verified before any upstream struct is
    /// bound so the mechanism itself is proven first.
    ///
    /// ```c
    /// typedef struct {
    ///     uint8_t  a;
    ///     uint32_t b;
    ///     void    *c;
    ///     double   d;
    /// } goldberry_probe_self_t;
    /// ```
    public static final NativeStructLayout PROBE_SELF = new NativeStructLayout(
            "goldberry_probe_self_t",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_BYTE.withName("a"),
                    MemoryLayout.paddingLayout(3),
                    ValueLayout.JAVA_INT.withName("b"),
                    ValueLayout.ADDRESS.withName("c"),
                    ValueLayout.JAVA_DOUBLE.withName("d")));

    /// The layout table's own entry type.
    ///
    /// ```c
    /// typedef struct {
    ///     const char *struct_name;
    ///     const char *field_name;
    ///     uint32_t    size;
    ///     uint32_t    offset;
    ///     uint32_t    alignment;
    /// } goldberry_layout_entry_t;
    /// ```
    ///
    /// Not in [#registry()], and not registered in the C table either: it is the
    /// type used to *read* that table, so verifying it against itself would mean
    /// dereferencing pointers already known to be suspect. It is validated
    /// indirectly — if this layout were wrong, no entry would parse and
    /// [LayoutProbe] would reject the table before reading any string.
    static final MemoryLayout LAYOUT_ENTRY = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("struct_name"),
            ValueLayout.ADDRESS.withName("field_name"),
            ValueLayout.JAVA_INT.withName("size"),
            ValueLayout.JAVA_INT.withName("offset"),
            ValueLayout.JAVA_INT.withName("alignment"),
            MemoryLayout.paddingLayout(4));

    /// Yoga's measured size — what a measure callback returns *by value*.
    ///
    /// Two floats and no padding, which is what makes it awkward: on SysV x86-64
    /// the pair returns packed in XMM0, on AArch64 as a homogeneous float
    /// aggregate in `s0`/`s1`, on Win64 folded into RAX. Sizes and offsets are
    /// the same everywhere, so this row cannot catch a wrong return convention —
    /// only [io.github.digitalsmile.goldberry.natives.yoga.MeasureCallback]'s
    /// round trip through C can. It is registered because the layout is what the
    /// upcall's [java.lang.foreign.FunctionDescriptor] is built from, and that
    /// much is checkable.
    ///
    /// ```c
    /// typedef struct YGSize {
    ///     float width;
    ///     float height;
    /// } YGSize;
    /// ```
    public static final NativeStructLayout YG_SIZE = new NativeStructLayout(
            "YGSize",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_FLOAT.withName("width"),
                    ValueLayout.JAVA_FLOAT.withName("height")));

    private Layouts() {
    }

    /// Every layout that must agree with the compiled library.
    ///
    /// Upstream structs join this list as they are bound — `SDL_Event` next, for
    /// the M0 window.
    public static List<NativeStructLayout> registry() {
        return List.of(PROBE_SELF, YG_SIZE);
    }
}
