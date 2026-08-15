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

    /// SDL's event union.
    ///
    /// Only the extent is modelled. Goldberry allocates one of these, hands SDL
    /// the pointer, and reads the arms it understands — so what has to be right is
    /// how much memory SDL is entitled to fill. Model it too small and every event
    /// with a large arm overflows the buffer.
    ///
    /// SDL pads the union to a fixed 128 bytes explicitly, precisely so MSVC and
    /// GCC agree, which is why this is one of the few upstream structs whose size
    /// is the same everywhere.
    /// Modelled as sixteen longs rather than 128 bytes: the union's alignment is
    /// its widest member's, which is 8, and a byte array would declare 1. An
    /// under-aligned allocation is the kind of thing that works on x86 and faults
    /// on other targets — and the layout probe says so on every one of them.
    public static final NativeStructLayout SDL_EVENT = new NativeStructLayout(
            "SDL_Event",
            MemoryLayout.structLayout(
                    MemoryLayout.sequenceLayout(16, ValueLayout.JAVA_LONG)));

    /// The header every SDL event arm starts with.
    ///
    /// ```c
    /// typedef struct SDL_CommonEvent {
    ///     Uint32 type;
    ///     Uint32 reserved;
    ///     Uint64 timestamp;
    /// } SDL_CommonEvent;
    /// ```
    public static final NativeStructLayout SDL_COMMON_EVENT = new NativeStructLayout(
            "SDL_CommonEvent",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("type"),
                    MemoryLayout.paddingLayout(4),
                    ValueLayout.JAVA_LONG.withName("timestamp")));

    /// A window event.
    ///
    /// ```c
    /// typedef struct SDL_WindowEvent {
    ///     SDL_EventType type;
    ///     Uint32        reserved;
    ///     Uint64        timestamp;
    ///     SDL_WindowID  windowID;
    ///     Sint32        data1;
    ///     Sint32        data2;
    /// } SDL_WindowEvent;
    /// ```
    public static final NativeStructLayout SDL_WINDOW_EVENT = new NativeStructLayout(
            "SDL_WindowEvent",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("type"),
                    MemoryLayout.paddingLayout(4),
                    ValueLayout.JAVA_LONG.withName("timestamp"),
                    ValueLayout.JAVA_INT.withName("windowID"),
                    ValueLayout.JAVA_INT.withName("data1"),
                    ValueLayout.JAVA_INT.withName("data2"),
                    MemoryLayout.paddingLayout(4)));

    /// A window's CPU-side surface — where the present path writes pixels.
    ///
    /// ```c
    /// struct SDL_Surface {
    ///     SDL_SurfaceFlags flags;
    ///     SDL_PixelFormat  format;
    ///     int   w, h, pitch;
    ///     void *pixels;
    ///     int   refcount;
    ///     void *reserved;
    /// };
    /// ```
    public static final NativeStructLayout SDL_SURFACE = new NativeStructLayout(
            "SDL_Surface",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("flags"),
                    ValueLayout.JAVA_INT.withName("format"),
                    ValueLayout.JAVA_INT.withName("w"),
                    ValueLayout.JAVA_INT.withName("h"),
                    ValueLayout.JAVA_INT.withName("pitch"),
                    MemoryLayout.paddingLayout(4),
                    ValueLayout.ADDRESS.withName("pixels"),
                    ValueLayout.JAVA_INT.withName("refcount"),
                    MemoryLayout.paddingLayout(4),
                    ValueLayout.ADDRESS.withName("reserved")));

    /// ```c
    /// typedef struct SDL_Rect { int x, y, w, h; } SDL_Rect;
    /// ```
    ///
    /// How damage rectangles reach `SDL_UpdateWindowSurfaceRects`.
    public static final NativeStructLayout SDL_RECT = new NativeStructLayout(
            "SDL_Rect",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("x"),
                    ValueLayout.JAVA_INT.withName("y"),
                    ValueLayout.JAVA_INT.withName("w"),
                    ValueLayout.JAVA_INT.withName("h")));

    /// Blend2D's entire object model — `BLObjectDetail`.
    ///
    /// Every Blend2D "core" object is exactly one of these and nothing else:
    /// `BLImageCore`, `BLContextCore`, `BLPathCore` are each a single 16-byte
    /// union in which a static payload and a pointer to a dynamic `Impl` are
    /// overlapped. That is why there is one layout here rather than one per
    /// type, and why the C table registers `BLImageCore` and `BLContextCore`
    /// too: those two rows are the assertion that they really are this shape.
    ///
    /// Modelled as two longs rather than sixteen bytes, for the reason
    /// [#SDL_EVENT] is: the union's alignment is its widest member's, and a byte
    /// array would declare 1. Its contents are Blend2D's business — Goldberry
    /// allocates it, hands over the pointer, and never reads a field.
    public static final NativeStructLayout BL_OBJECT_DETAIL = new NativeStructLayout(
            "BLObjectDetail",
            MemoryLayout.structLayout(
                    MemoryLayout.sequenceLayout(2, ValueLayout.JAVA_LONG)));

    /// Where an image's pixels actually are — the out-parameter of
    /// `bl_image_get_data`.
    ///
    /// ```c
    /// struct BLImageData {
    ///     void    *pixel_data;
    ///     intptr_t stride;
    ///     BLSizeI  size;
    ///     uint32_t format;
    ///     uint32_t flags;
    /// };
    /// ```
    ///
    /// `stride` is signed and may be negative, which means the image starts at
    /// the bottom-left. Goldberry never creates one that way, but reading the
    /// field as unsigned would turn that into an enormous positive number rather
    /// than an obvious one.
    /// `size` is nested rather than flattened into two ints: the C table reports
    /// it as one 8-byte field, because that is what it is, and the verifier
    /// compares field for field.
    public static final NativeStructLayout BL_IMAGE_DATA = new NativeStructLayout(
            "BLImageData",
            MemoryLayout.structLayout(
                    ValueLayout.ADDRESS.withName("pixel_data"),
                    ValueLayout.JAVA_LONG.withName("stride"),
                    MemoryLayout.structLayout(
                                    ValueLayout.JAVA_INT.withName("w"),
                                    ValueLayout.JAVA_INT.withName("h"))
                            .withName("size"),
                    ValueLayout.JAVA_INT.withName("format"),
                    ValueLayout.JAVA_INT.withName("flags")));

    /// How a rendering context is configured.
    ///
    /// ```c
    /// struct BLContextCreateInfo {
    ///     uint32_t flags, thread_count, cpu_features;
    ///     uint32_t command_queue_limit, saved_state_limit;
    ///     BLPointI pixel_origin;
    ///     uint32_t reserved[1];
    /// };
    /// ```
    ///
    /// All zeros means a synchronous context on the calling thread, which is
    /// what Goldberry asks for today. `thread_count` is the knob behind
    /// Blend2D's banded multithreading (ADR-0002) and is deliberately left at
    /// zero until there is a frame worth measuring.
    public static final NativeStructLayout BL_CONTEXT_CREATE_INFO = new NativeStructLayout(
            "BLContextCreateInfo",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("flags"),
                    ValueLayout.JAVA_INT.withName("thread_count"),
                    ValueLayout.JAVA_INT.withName("cpu_features"),
                    ValueLayout.JAVA_INT.withName("command_queue_limit"),
                    ValueLayout.JAVA_INT.withName("saved_state_limit"),
                    MemoryLayout.structLayout(
                                    ValueLayout.JAVA_INT.withName("x"),
                                    ValueLayout.JAVA_INT.withName("y"))
                            .withName("pixel_origin"),
                    // `reserved[1]`, unnamed here: there is no C row to compare
                    // it against and nothing may read it.
                    MemoryLayout.paddingLayout(4)));

    /// ```c
    /// struct BLRect { double x, y, w, h; };
    /// ```
    ///
    /// Doubles, not integers. Blend2D's coordinate space is real-valued, which
    /// is what lets a logical coordinate land between two physical pixels and be
    /// antialiased rather than snapped to one of them — the difference between a
    /// 1px border at 150% looking crisp and looking like it moved.
    public static final NativeStructLayout BL_RECT = new NativeStructLayout(
            "BLRect",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_DOUBLE.withName("x"),
                    ValueLayout.JAVA_DOUBLE.withName("y"),
                    ValueLayout.JAVA_DOUBLE.withName("w"),
                    ValueLayout.JAVA_DOUBLE.withName("h")));

    /// ```c
    /// struct BLSizeI { int w, h; };
    /// ```
    public static final NativeStructLayout BL_SIZE_I = new NativeStructLayout(
            "BLSizeI",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("w"),
                    ValueLayout.JAVA_INT.withName("h")));

    /// ```c
    /// struct BLPointI { int x, y; };
    /// ```
    public static final NativeStructLayout BL_POINT_I = new NativeStructLayout(
            "BLPointI",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("x"),
                    ValueLayout.JAVA_INT.withName("y")));

    /// ```c
    /// struct BLPoint { double x, y; };
    /// ```
    ///
    /// Where a glyph run's origin — its baseline — is given. Doubles for the
    /// reason [#BL_RECT] is: a baseline snapped to whole pixels would quantise
    /// line spacing, and at 1.5&times; that is a visibly uneven paragraph.
    public static final NativeStructLayout BL_POINT = new NativeStructLayout(
            "BLPoint",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_DOUBLE.withName("x"),
                    ValueLayout.JAVA_DOUBLE.withName("y")));

    /// A run of positioned glyphs, as Blend2D reads one — `BLGlyphRun`.
    ///
    /// A **descriptor**, not a container: two pointers into memory the caller
    /// owns, plus the stride of each array. That is what lets Goldberry hand
    /// over glyph ids and placements it staged itself rather than copying them
    /// into a Blend2D-shaped buffer first.
    ///
    /// ```c
    /// struct BLGlyphRun {
    ///     void    *glyph_data;
    ///     void    *placement_data;
    ///     size_t   size;
    ///     uint8_t  reserved;
    ///     uint8_t  placement_type;
    ///     int8_t   glyph_advance;
    ///     int8_t   placement_advance;
    ///     uint32_t flags;
    /// };
    /// ```
    ///
    /// The two `*_advance` fields are **strides in bytes**, not advances in the
    /// typographic sense — Blend2D's naming, kept rather than improved on,
    /// because a field renamed at the boundary is a field nobody can grep for.
    /// They are `int8_t`: a stride wider than 127 bytes cannot be expressed, and
    /// Goldberry's are 4 and 16.
    public static final NativeStructLayout BL_GLYPH_RUN = new NativeStructLayout(
            "BLGlyphRun",
            MemoryLayout.structLayout(
                    ValueLayout.ADDRESS.withName("glyph_data"),
                    ValueLayout.ADDRESS.withName("placement_data"),
                    ValueLayout.JAVA_LONG.withName("size"),
                    ValueLayout.JAVA_BYTE.withName("reserved"),
                    ValueLayout.JAVA_BYTE.withName("placement_type"),
                    ValueLayout.JAVA_BYTE.withName("glyph_advance"),
                    ValueLayout.JAVA_BYTE.withName("placement_advance"),
                    ValueLayout.JAVA_INT.withName("flags")));

    /// Where one glyph goes — `BLGlyphPlacement`.
    ///
    /// ```c
    /// struct BLGlyphPlacement {
    ///     BLPointI placement;
    ///     BLPointI advance;
    /// };
    /// ```
    ///
    /// `placement` moves the glyph without moving the pen; `advance` moves the
    /// pen. Those are the same two things HarfBuzz reports as offset and
    /// advance, in the same order and the same width, which is why the crossing
    /// is four `int` writes per glyph and not a conversion.
    ///
    /// The units are **font design units**, because Blend2D multiplies these by
    /// the font matrix — see [ADR-0034][io.github.digitalsmile.goldberry.natives.blend2d.BlendFont].
    public static final NativeStructLayout BL_GLYPH_PLACEMENT = new NativeStructLayout(
            "BLGlyphPlacement",
            MemoryLayout.structLayout(
                    MemoryLayout.structLayout(
                                    ValueLayout.JAVA_INT.withName("x"),
                                    ValueLayout.JAVA_INT.withName("y"))
                            .withName("placement"),
                    MemoryLayout.structLayout(
                                    ValueLayout.JAVA_INT.withName("x"),
                                    ValueLayout.JAVA_INT.withName("y"))
                            .withName("advance")));

    /// A font's metrics at the size it was created with — `BLFontMetrics`.
    ///
    /// Sixteen floats, already scaled: `ascent` is how far above the baseline
    /// this font reaches *at this size*, in the context's own units. The face's
    /// `BLFontDesignMetrics` carries the same numbers in design units and is
    /// deliberately not bound — one of the two has to be the one a paint pass
    /// reaches for, and it is this one.
    ///
    /// The C declaration overlaps `ascent`/`v_ascent` with an
    /// `ascent_by_orientation[2]` array in an anonymous union, and likewise for
    /// descent. Same memory, so a flat run of floats models it exactly.
    public static final NativeStructLayout BL_FONT_METRICS = new NativeStructLayout(
            "BLFontMetrics",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_FLOAT.withName("size"),
                    ValueLayout.JAVA_FLOAT.withName("ascent"),
                    ValueLayout.JAVA_FLOAT.withName("v_ascent"),
                    ValueLayout.JAVA_FLOAT.withName("descent"),
                    ValueLayout.JAVA_FLOAT.withName("v_descent"),
                    ValueLayout.JAVA_FLOAT.withName("line_gap"),
                    ValueLayout.JAVA_FLOAT.withName("x_height"),
                    ValueLayout.JAVA_FLOAT.withName("cap_height"),
                    ValueLayout.JAVA_FLOAT.withName("x_min"),
                    ValueLayout.JAVA_FLOAT.withName("y_min"),
                    ValueLayout.JAVA_FLOAT.withName("x_max"),
                    ValueLayout.JAVA_FLOAT.withName("y_max"),
                    ValueLayout.JAVA_FLOAT.withName("underline_position"),
                    ValueLayout.JAVA_FLOAT.withName("underline_thickness"),
                    ValueLayout.JAVA_FLOAT.withName("strikethrough_position"),
                    ValueLayout.JAVA_FLOAT.withName("strikethrough_thickness")));

    /// Which Blend2D is linked in.
    ///
    /// A build fact rather than a runtime one, exactly like [SdlVersion][
    /// io.github.digitalsmile.goldberry.natives.sdl.SdlVersion]: the library is
    /// statically linked, so there is no system Blend2D to disagree with. It
    /// matters more than it used to — the ref is now a commit SHA (ADR-0030),
    /// and this is how a bug report says which one.
    ///
    /// `compiler_info` is a fixed 32-byte char array, not a pointer.
    public static final NativeStructLayout BL_RUNTIME_BUILD_INFO = new NativeStructLayout(
            "BLRuntimeBuildInfo",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("major_version"),
                    ValueLayout.JAVA_INT.withName("minor_version"),
                    ValueLayout.JAVA_INT.withName("patch_version"),
                    ValueLayout.JAVA_INT.withName("build_type"),
                    ValueLayout.JAVA_INT.withName("baseline_cpu_features"),
                    ValueLayout.JAVA_INT.withName("supported_cpu_features"),
                    ValueLayout.JAVA_INT.withName("max_image_size"),
                    ValueLayout.JAVA_INT.withName("max_thread_count"),
                    // `reserved[2]`, unnamed for the same reason as above.
                    MemoryLayout.paddingLayout(8),
                    MemoryLayout.sequenceLayout(32, ValueLayout.JAVA_BYTE).withName("compiler_info")));

    /// One shaped glyph's identity — `hb_glyph_info_t`.
    ///
    /// ```c
    /// typedef struct hb_glyph_info_t {
    ///     hb_codepoint_t codepoint;
    ///     hb_mask_t      mask;     /* private */
    ///     uint32_t       cluster;
    ///     hb_var_int_t   var1;     /* private */
    ///     hb_var_int_t   var2;     /* private */
    /// } hb_glyph_info_t;
    /// ```
    ///
    /// Three of the five members are private to HarfBuzz and are modelled as
    /// padding: they must never be read, but they are part of the **stride**,
    /// and the stride is what matters. Shaping hands back a pointer to an array
    /// of these and Goldberry reads it in place rather than copying, so a size
    /// that is wrong by four bytes gives a correct first glyph and garbage for
    /// every one after it.
    ///
    /// `codepoint` is a glyph id after shaping, not a Unicode code point. It is
    /// a code point only *before* — the field is reused, which is HarfBuzz's
    /// single most confusing piece of naming.
    public static final NativeStructLayout HB_GLYPH_INFO = new NativeStructLayout(
            "hb_glyph_info_t",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("codepoint"),
                    MemoryLayout.paddingLayout(4), // mask
                    ValueLayout.JAVA_INT.withName("cluster"),
                    MemoryLayout.paddingLayout(8))); // var1, var2

    /// Where a shaped glyph goes — `hb_glyph_position_t`.
    ///
    /// ```c
    /// typedef struct hb_glyph_position_t {
    ///     hb_position_t x_advance, y_advance, x_offset, y_offset;
    ///     hb_var_int_t  var;       /* private */
    /// } hb_glyph_position_t;
    /// ```
    ///
    /// `hb_position_t` is a **signed** 32-bit integer, in whatever units the
    /// font's scale was set to. Reading it unsigned would turn a leftward
    /// kerning adjustment — which is ordinary — into four billion.
    public static final NativeStructLayout HB_GLYPH_POSITION = new NativeStructLayout(
            "hb_glyph_position_t",
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("x_advance"),
                    ValueLayout.JAVA_INT.withName("y_advance"),
                    ValueLayout.JAVA_INT.withName("x_offset"),
                    ValueLayout.JAVA_INT.withName("y_offset"),
                    MemoryLayout.paddingLayout(4))); // var

    private Layouts() {
    }

    /// Every layout that must agree with the compiled library.
    public static List<NativeStructLayout> registry() {
        return List.of(
                PROBE_SELF,
                YG_SIZE,
                SDL_EVENT,
                SDL_COMMON_EVENT,
                SDL_WINDOW_EVENT,
                SDL_SURFACE,
                SDL_RECT,
                BL_OBJECT_DETAIL,
                BL_IMAGE_DATA,
                BL_CONTEXT_CREATE_INFO,
                BL_RECT,
                BL_SIZE_I,
                BL_POINT_I,
                BL_POINT,
                BL_GLYPH_RUN,
                BL_GLYPH_PLACEMENT,
                BL_FONT_METRICS,
                BL_RUNTIME_BUILD_INFO,
                HB_GLYPH_INFO,
                HB_GLYPH_POSITION);
    }
}
