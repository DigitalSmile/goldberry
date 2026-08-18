/*
 * libgoldberry translation unit.
 *
 * Deliberately tiny. Goldberry binds its native dependencies through
 * hand-written FFM downcalls (ADR-0010), not through C glue, so this file holds
 * only the three things that cannot live on the Java side:
 *
 *   1. An ABI probe, so the Java layer can refuse a mismatched library instead
 *      of discovering the mismatch as a segfault.
 *
 *   2. The layout table -- sizeof, alignment, and offsetof for every struct
 *      Goldberry binds, as the C compiler computed them for this exact target.
 *      A Java test asserts the hand-written MemoryLayouts agree with it. This
 *      table is the entire safety argument for hand-writing the bindings:
 *      without it, a wrong offset is silent memory corruption on one platform.
 *
 *   3. A caller for the measure callback, because an upcall that returns a
 *      struct BY VALUE cannot be proven from Java alone -- something compiled by
 *      the target's own C compiler has to receive the struct and say what
 *      arrived (ADR-0017).
 *
 * See docs/ARCHITECTURE.md §3.1 and §3.2.
 */

#include <stddef.h>
#include <stdint.h>

/*
 * For YGSize and YGMeasureFunc. The shim links yogacore, so this is the real
 * declaration rather than a copy of it -- which is the point: a copy would agree
 * with the Java layout and both could be wrong together.
 */
#include <yoga/Yoga.h>

/*
 * For SDL_Event, SDL_Surface, and the SDL_EVENT_* values. Same reasoning as
 * Yoga: these are the real declarations, so a Java constant that disagrees with
 * the SDL that was actually compiled in fails the verification test rather than
 * dispatching on an event number nothing sends.
 */
#include <SDL3/SDL.h>

/*
 * For Blend2D's object model. Every "core" object -- BLImageCore, BLContextCore
 * and the rest -- is one 16-byte BLObjectDetail union, and the Java side
 * allocates them by that size. If that ever stopped being true the bindings
 * would hand Blend2D a segment too small to initialise, so the equality is
 * asserted here rather than assumed. See ADR-0031.
 *
 * blend2d.h is C-compatible: the C++ class bodies are all behind __cplusplus,
 * and the C API is what remains.
 */
#include <blend2d/blend2d.h>

/*
 * For hb_glyph_info_t and hb_glyph_position_t. Shaping returns two parallel
 * arrays of these, read directly out of HarfBuzz's own memory rather than
 * copied -- so their strides have to be exactly right or every glyph after the
 * first lands at the wrong offset. See ADR-0032.
 */
#include <hb.h>

#if defined(_WIN32)
#define GOLDBERRY_EXPORT __declspec(dllexport)
#else
#define GOLDBERRY_EXPORT __attribute__((visibility("default")))
#endif

/* Bumped whenever the exported surface changes shape. */
#define GOLDBERRY_ABI_VERSION 8u

GOLDBERRY_EXPORT uint32_t goldberry_abi_version(void) {
    return GOLDBERRY_ABI_VERSION;
}

/* ------------------------------------------------------------------------ */
/* Layout table                                                             */
/* ------------------------------------------------------------------------ */

typedef struct {
    const char *struct_name;
    /* NULL means the row describes the struct itself rather than a field. */
    const char *field_name;
    uint32_t size;
    uint32_t offset;
    uint32_t alignment;
} goldberry_layout_entry_t;

#define GB_STRUCT(type) \
    { #type, NULL, (uint32_t) sizeof(type), 0u, (uint32_t) _Alignof(type) }

#define GB_FIELD(type, field) \
    { #type, #field, (uint32_t) sizeof(((type *) 0)->field), \
      (uint32_t) offsetof(type, field), 0u }

/*
 * Scalar rows carry the primitive widths that differ across our targets. `long`
 * is the classic one: 4 bytes on Win64, 8 on Linux and macOS. Recording it here
 * means the Java side's assumption is checked rather than believed.
 */
#define GB_SCALAR(name, type) \
    { "<scalar>", name, (uint32_t) sizeof(type), 0u, (uint32_t) _Alignof(type) }

/*
 * Constant rows carry a value rather than a size. Enumerator values are exactly
 * as easy to get wrong as struct offsets and exactly as silent when wrong: a Java
 * constant for SDL_EVENT_WINDOW_CLOSE_REQUESTED that is off by one dispatches on
 * an event nothing sends, and the window simply never closes. Reporting them here
 * means the C compiler's value is what the test compares against.
 */
#define GB_CONSTANT(name, value) \
    { "<constant>", name, (uint32_t) (value), 0u, 0u }

/*
 * Canary struct. Its fields are chosen so that padding, not just field order,
 * has to be modelled correctly on the Java side. It is verified before any
 * upstream struct is bound, so the mechanism itself is proven first.
 */
typedef struct {
    uint8_t a;
    uint32_t b;
    void *c;
    double d;
} goldberry_probe_self_t;

static const goldberry_layout_entry_t GOLDBERRY_LAYOUTS[] = {
    GB_STRUCT(goldberry_probe_self_t),
    GB_FIELD(goldberry_probe_self_t, a),
    GB_FIELD(goldberry_probe_self_t, b),
    GB_FIELD(goldberry_probe_self_t, c),
    GB_FIELD(goldberry_probe_self_t, d),

    GB_SCALAR("char", char),
    GB_SCALAR("short", short),
    GB_SCALAR("int", int),
    GB_SCALAR("long", long),
    GB_SCALAR("long long", long long),
    GB_SCALAR("float", float),
    GB_SCALAR("double", double),
    GB_SCALAR("pointer", void *),
    GB_SCALAR("size_t", size_t),

    /*
     * Upstream structs are registered here as they are bound. A struct bound in
     * Java but absent here fails the verification test, which is the point.
     */
    GB_STRUCT(YGSize),
    GB_FIELD(YGSize, width),
    GB_FIELD(YGSize, height),

    /*
     * SDL_Event is a union. Only its size and alignment are modelled -- Goldberry
     * allocates one and reads the arms it understands, so the union's own extent
     * is what has to be right. Reading it as too small a segment is a buffer
     * overflow every time SDL fills in a large arm.
     */
    GB_STRUCT(SDL_Event),
    GB_STRUCT(SDL_CommonEvent),
    GB_FIELD(SDL_CommonEvent, type),
    GB_FIELD(SDL_CommonEvent, timestamp),
    GB_STRUCT(SDL_WindowEvent),
    GB_FIELD(SDL_WindowEvent, type),
    GB_FIELD(SDL_WindowEvent, timestamp),
    GB_FIELD(SDL_WindowEvent, windowID),
    GB_FIELD(SDL_WindowEvent, data1),
    GB_FIELD(SDL_WindowEvent, data2),

    GB_STRUCT(SDL_MouseMotionEvent),
    GB_FIELD(SDL_MouseMotionEvent, type),
    GB_FIELD(SDL_MouseMotionEvent, timestamp),
    GB_FIELD(SDL_MouseMotionEvent, windowID),
    GB_FIELD(SDL_MouseMotionEvent, which),
    GB_FIELD(SDL_MouseMotionEvent, state),
    GB_FIELD(SDL_MouseMotionEvent, x),
    GB_FIELD(SDL_MouseMotionEvent, y),
    GB_FIELD(SDL_MouseMotionEvent, xrel),
    GB_FIELD(SDL_MouseMotionEvent, yrel),

    GB_STRUCT(SDL_MouseButtonEvent),
    GB_FIELD(SDL_MouseButtonEvent, type),
    GB_FIELD(SDL_MouseButtonEvent, timestamp),
    GB_FIELD(SDL_MouseButtonEvent, windowID),
    GB_FIELD(SDL_MouseButtonEvent, which),
    GB_FIELD(SDL_MouseButtonEvent, button),
    GB_FIELD(SDL_MouseButtonEvent, down),
    GB_FIELD(SDL_MouseButtonEvent, clicks),
    GB_FIELD(SDL_MouseButtonEvent, padding),
    GB_FIELD(SDL_MouseButtonEvent, x),
    GB_FIELD(SDL_MouseButtonEvent, y),

    GB_STRUCT(SDL_MouseWheelEvent),
    GB_FIELD(SDL_MouseWheelEvent, type),
    GB_FIELD(SDL_MouseWheelEvent, timestamp),
    GB_FIELD(SDL_MouseWheelEvent, windowID),
    GB_FIELD(SDL_MouseWheelEvent, which),
    GB_FIELD(SDL_MouseWheelEvent, x),
    GB_FIELD(SDL_MouseWheelEvent, y),
    GB_FIELD(SDL_MouseWheelEvent, direction),
    GB_FIELD(SDL_MouseWheelEvent, mouse_x),
    GB_FIELD(SDL_MouseWheelEvent, mouse_y),
    GB_FIELD(SDL_MouseWheelEvent, integer_x),
    GB_FIELD(SDL_MouseWheelEvent, integer_y),

    GB_STRUCT(SDL_KeyboardEvent),
    GB_FIELD(SDL_KeyboardEvent, type),
    GB_FIELD(SDL_KeyboardEvent, timestamp),
    GB_FIELD(SDL_KeyboardEvent, windowID),
    GB_FIELD(SDL_KeyboardEvent, which),
    GB_FIELD(SDL_KeyboardEvent, scancode),
    GB_FIELD(SDL_KeyboardEvent, key),
    GB_FIELD(SDL_KeyboardEvent, mod),
    GB_FIELD(SDL_KeyboardEvent, raw),
    GB_FIELD(SDL_KeyboardEvent, down),
    GB_FIELD(SDL_KeyboardEvent, repeat),

    GB_STRUCT(SDL_TextInputEvent),
    GB_FIELD(SDL_TextInputEvent, type),
    GB_FIELD(SDL_TextInputEvent, timestamp),
    GB_FIELD(SDL_TextInputEvent, windowID),
    GB_FIELD(SDL_TextInputEvent, text),

    GB_STRUCT(SDL_Surface),
    GB_FIELD(SDL_Surface, flags),
    GB_FIELD(SDL_Surface, format),
    GB_FIELD(SDL_Surface, w),
    GB_FIELD(SDL_Surface, h),
    GB_FIELD(SDL_Surface, pitch),
    GB_FIELD(SDL_Surface, pixels),
    GB_FIELD(SDL_Surface, refcount),
    GB_FIELD(SDL_Surface, reserved),

    GB_STRUCT(SDL_DisplayMode),
    GB_FIELD(SDL_DisplayMode, displayID),
    GB_FIELD(SDL_DisplayMode, format),
    GB_FIELD(SDL_DisplayMode, w),
    GB_FIELD(SDL_DisplayMode, h),
    GB_FIELD(SDL_DisplayMode, pixel_density),
    GB_FIELD(SDL_DisplayMode, refresh_rate),
    GB_FIELD(SDL_DisplayMode, refresh_rate_numerator),
    GB_FIELD(SDL_DisplayMode, refresh_rate_denominator),
    GB_FIELD(SDL_DisplayMode, internal),

    GB_STRUCT(SDL_Rect),
    GB_FIELD(SDL_Rect, x),
    GB_FIELD(SDL_Rect, y),
    GB_FIELD(SDL_Rect, w),
    GB_FIELD(SDL_Rect, h),

    /* Event types Goldberry dispatches on. */
    GB_CONSTANT("SDL_EVENT_QUIT", SDL_EVENT_QUIT),
    GB_CONSTANT("SDL_EVENT_KEY_DOWN", SDL_EVENT_KEY_DOWN),
    GB_CONSTANT("SDL_EVENT_KEY_UP", SDL_EVENT_KEY_UP),
    GB_CONSTANT("SDL_EVENT_TEXT_INPUT", SDL_EVENT_TEXT_INPUT),
    GB_CONSTANT("SDL_EVENT_MOUSE_MOTION", SDL_EVENT_MOUSE_MOTION),
    GB_CONSTANT("SDL_EVENT_MOUSE_BUTTON_DOWN", SDL_EVENT_MOUSE_BUTTON_DOWN),
    GB_CONSTANT("SDL_EVENT_MOUSE_BUTTON_UP", SDL_EVENT_MOUSE_BUTTON_UP),
    GB_CONSTANT("SDL_EVENT_MOUSE_WHEEL", SDL_EVENT_MOUSE_WHEEL),
    GB_CONSTANT("SDL_EVENT_WINDOW_EXPOSED", SDL_EVENT_WINDOW_EXPOSED),
    GB_CONSTANT("SDL_EVENT_WINDOW_RESIZED", SDL_EVENT_WINDOW_RESIZED),
    GB_CONSTANT("SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED", SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED),
    GB_CONSTANT("SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED", SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED),
    GB_CONSTANT("SDL_EVENT_WINDOW_CLOSE_REQUESTED", SDL_EVENT_WINDOW_CLOSE_REQUESTED),
    GB_CONSTANT("SDL_EVENT_USER", SDL_EVENT_USER),

    /* Window creation flags. */
    GB_CONSTANT("SDL_WINDOW_RESIZABLE", SDL_WINDOW_RESIZABLE),
    GB_CONSTANT("SDL_WINDOW_BORDERLESS", SDL_WINDOW_BORDERLESS),
    GB_CONSTANT("SDL_WINDOW_HIGH_PIXEL_DENSITY", SDL_WINDOW_HIGH_PIXEL_DENSITY),
    GB_CONSTANT("SDL_WINDOW_HIDDEN", SDL_WINDOW_HIDDEN),
    GB_CONSTANT("SDL_WINDOW_POPUP_MENU", SDL_WINDOW_POPUP_MENU),
    GB_CONSTANT("SDL_WINDOW_TOOLTIP", SDL_WINDOW_TOOLTIP),
    GB_CONSTANT("SDL_WINDOW_NOT_FOCUSABLE", SDL_WINDOW_NOT_FOCUSABLE),
    GB_CONSTANT("SDL_WINDOW_TRANSPARENT", SDL_WINDOW_TRANSPARENT),

    /*
     * Which way round a wheel event's values are. FLIPPED is what "natural
     * scrolling" sets, and a reader that ignores it scrolls backwards.
     */
    GB_CONSTANT("SDL_MOUSEWHEEL_NORMAL", SDL_MOUSEWHEEL_NORMAL),
    GB_CONSTANT("SDL_MOUSEWHEEL_FLIPPED", SDL_MOUSEWHEEL_FLIPPED),

    /*
     * The system cursor shapes §7.3 names. Every one is an ordinal in an enum
     * upstream is free to extend in the middle -- SDL_SYSTEM_CURSOR_POINTER is
     * 11 today and was not in SDL2 at all -- so these are values to check, not
     * values to copy once and trust.
     */
    GB_CONSTANT("SDL_SYSTEM_CURSOR_DEFAULT", SDL_SYSTEM_CURSOR_DEFAULT),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_TEXT", SDL_SYSTEM_CURSOR_TEXT),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_WAIT", SDL_SYSTEM_CURSOR_WAIT),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_CROSSHAIR", SDL_SYSTEM_CURSOR_CROSSHAIR),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_PROGRESS", SDL_SYSTEM_CURSOR_PROGRESS),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_NWSE_RESIZE", SDL_SYSTEM_CURSOR_NWSE_RESIZE),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_NESW_RESIZE", SDL_SYSTEM_CURSOR_NESW_RESIZE),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_EW_RESIZE", SDL_SYSTEM_CURSOR_EW_RESIZE),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_NS_RESIZE", SDL_SYSTEM_CURSOR_NS_RESIZE),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_MOVE", SDL_SYSTEM_CURSOR_MOVE),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_NOT_ALLOWED", SDL_SYSTEM_CURSOR_NOT_ALLOWED),
    GB_CONSTANT("SDL_SYSTEM_CURSOR_POINTER", SDL_SYSTEM_CURSOR_POINTER),

    /* Surface formats the CPU present path accepts. */
    GB_CONSTANT("SDL_PIXELFORMAT_XRGB8888", SDL_PIXELFORMAT_XRGB8888),
    GB_CONSTANT("SDL_PIXELFORMAT_ARGB8888", SDL_PIXELFORMAT_ARGB8888),

    /*
     * Yoga's enumerators (ADR-0029).
     *
     * All of them, not just the ones a widget is likely to use: the Java enums
     * are the complete C enums, and a Java constant nothing checks is exactly
     * the constant that will be wrong. They are all small and non-negative, so
     * the uint32_t the table carries loses nothing.
     *
     * These are the values that decide what a layout looks like. YGAlignCenter
     * is 2 and YGJustifyCenter is 1; getting that pair backwards produces a
     * layout that is merely wrong, never an error, on every platform at once.
     */
    GB_CONSTANT("YGAlignAuto", YGAlignAuto),
    GB_CONSTANT("YGAlignFlexStart", YGAlignFlexStart),
    GB_CONSTANT("YGAlignCenter", YGAlignCenter),
    GB_CONSTANT("YGAlignFlexEnd", YGAlignFlexEnd),
    GB_CONSTANT("YGAlignStretch", YGAlignStretch),
    GB_CONSTANT("YGAlignBaseline", YGAlignBaseline),
    GB_CONSTANT("YGAlignSpaceBetween", YGAlignSpaceBetween),
    GB_CONSTANT("YGAlignSpaceAround", YGAlignSpaceAround),
    GB_CONSTANT("YGAlignSpaceEvenly", YGAlignSpaceEvenly),

    GB_CONSTANT("YGDirectionInherit", YGDirectionInherit),
    GB_CONSTANT("YGDirectionLTR", YGDirectionLTR),
    GB_CONSTANT("YGDirectionRTL", YGDirectionRTL),

    GB_CONSTANT("YGDisplayFlex", YGDisplayFlex),
    GB_CONSTANT("YGDisplayNone", YGDisplayNone),

    GB_CONSTANT("YGEdgeLeft", YGEdgeLeft),
    GB_CONSTANT("YGEdgeTop", YGEdgeTop),
    GB_CONSTANT("YGEdgeRight", YGEdgeRight),
    GB_CONSTANT("YGEdgeBottom", YGEdgeBottom),
    GB_CONSTANT("YGEdgeStart", YGEdgeStart),
    GB_CONSTANT("YGEdgeEnd", YGEdgeEnd),
    GB_CONSTANT("YGEdgeHorizontal", YGEdgeHorizontal),
    GB_CONSTANT("YGEdgeVertical", YGEdgeVertical),
    GB_CONSTANT("YGEdgeAll", YGEdgeAll),

    GB_CONSTANT("YGFlexDirectionColumn", YGFlexDirectionColumn),
    GB_CONSTANT("YGFlexDirectionColumnReverse", YGFlexDirectionColumnReverse),
    GB_CONSTANT("YGFlexDirectionRow", YGFlexDirectionRow),
    GB_CONSTANT("YGFlexDirectionRowReverse", YGFlexDirectionRowReverse),

    GB_CONSTANT("YGGutterColumn", YGGutterColumn),
    GB_CONSTANT("YGGutterRow", YGGutterRow),
    GB_CONSTANT("YGGutterAll", YGGutterAll),

    GB_CONSTANT("YGJustifyFlexStart", YGJustifyFlexStart),
    GB_CONSTANT("YGJustifyCenter", YGJustifyCenter),
    GB_CONSTANT("YGJustifyFlexEnd", YGJustifyFlexEnd),
    GB_CONSTANT("YGJustifySpaceBetween", YGJustifySpaceBetween),
    GB_CONSTANT("YGJustifySpaceAround", YGJustifySpaceAround),
    GB_CONSTANT("YGJustifySpaceEvenly", YGJustifySpaceEvenly),

    GB_CONSTANT("YGMeasureModeUndefined", YGMeasureModeUndefined),
    GB_CONSTANT("YGMeasureModeExactly", YGMeasureModeExactly),
    GB_CONSTANT("YGMeasureModeAtMost", YGMeasureModeAtMost),

    GB_CONSTANT("YGOverflowVisible", YGOverflowVisible),
    GB_CONSTANT("YGOverflowHidden", YGOverflowHidden),
    GB_CONSTANT("YGOverflowScroll", YGOverflowScroll),

    GB_CONSTANT("YGPositionTypeStatic", YGPositionTypeStatic),
    GB_CONSTANT("YGPositionTypeRelative", YGPositionTypeRelative),
    GB_CONSTANT("YGPositionTypeAbsolute", YGPositionTypeAbsolute),

    GB_CONSTANT("YGWrapNoWrap", YGWrapNoWrap),
    GB_CONSTANT("YGWrapWrap", YGWrapWrap),
    GB_CONSTANT("YGWrapWrapReverse", YGWrapWrapReverse),

    /*
     * Blend2D (ADR-0031).
     *
     * BLObjectDetail is the whole object model: every core object is exactly one
     * of these, static payload and dynamic Impl pointer overlapped in 16 bytes.
     * The Java side allocates BLImageCore and BLContextCore by this size, so the
     * three rows below are the assertion that they really are the same shape --
     * an assumption that costs a segment too small for Blend2D to initialise if
     * it is ever wrong.
     */
    GB_STRUCT(BLObjectDetail),
    GB_STRUCT(BLImageCore),
    GB_STRUCT(BLContextCore),

    /* The out-parameter of bl_image_get_data: where the pixels actually are. */
    GB_STRUCT(BLImageData),
    GB_FIELD(BLImageData, pixel_data),
    GB_FIELD(BLImageData, stride),
    GB_FIELD(BLImageData, size),
    GB_FIELD(BLImageData, format),
    GB_FIELD(BLImageData, flags),

    /* Passed by pointer to bl_context_init_as. Zeroed means synchronous. */
    GB_STRUCT(BLContextCreateInfo),
    GB_FIELD(BLContextCreateInfo, flags),
    GB_FIELD(BLContextCreateInfo, thread_count),
    GB_FIELD(BLContextCreateInfo, cpu_features),
    GB_FIELD(BLContextCreateInfo, command_queue_limit),
    GB_FIELD(BLContextCreateInfo, saved_state_limit),
    GB_FIELD(BLContextCreateInfo, pixel_origin),

    /* Geometry. BLRect is doubles -- Blend2D's coordinate space is real-valued,
     * which is what lets a logical coordinate land between physical pixels and
     * be antialiased rather than snapped. */
    GB_STRUCT(BLRect),
    GB_FIELD(BLRect, x),
    GB_FIELD(BLRect, y),
    GB_FIELD(BLRect, w),
    GB_FIELD(BLRect, h),

    GB_STRUCT(BLRectI),
    GB_FIELD(BLRectI, x),
    GB_FIELD(BLRectI, y),
    GB_FIELD(BLRectI, w),
    GB_FIELD(BLRectI, h),

    GB_STRUCT(BLSizeI),
    GB_FIELD(BLSizeI, w),
    GB_FIELD(BLSizeI, h),

    GB_STRUCT(BLPointI),
    GB_FIELD(BLPointI, x),
    GB_FIELD(BLPointI, y),

    /* Doubles, like BLRect and for the same reason: a glyph run's origin is a
     * baseline position, and a baseline that snapped to whole pixels would
     * quantise line spacing at fractional scales. */
    GB_STRUCT(BLPoint),
    GB_FIELD(BLPoint, x),
    GB_FIELD(BLPoint, y),

    /* The operand of BL_TRANSFORM_OP_ASSIGN, which crosses as void* -- so these
     * six offsets are the only thing standing between a CSS transform and a
     * frame that is skewed and reports BL_SUCCESS. The named members live in an
     * anonymous struct inside a union with a double[6], and the upstream header
     * carries a TODO to remove that union; if the order ever changes these rows
     * are what says so. */
    GB_STRUCT(BLMatrix2D),
    GB_FIELD(BLMatrix2D, m00),
    GB_FIELD(BLMatrix2D, m01),
    GB_FIELD(BLMatrix2D, m10),
    GB_FIELD(BLMatrix2D, m11),
    GB_FIELD(BLMatrix2D, m20),
    GB_FIELD(BLMatrix2D, m21),

    /*
     * Fonts and glyph runs (ADR-0034).
     *
     * The three font objects are BLObjectDetail-shaped like every other core
     * object, so these rows say the same thing BLImageCore's does.
     */
    GB_STRUCT(BLFontDataCore),
    GB_STRUCT(BLFontFaceCore),
    GB_STRUCT(BLFontCore),

    /*
     * BLGlyphRun is a descriptor, not a container: it points at somebody else's
     * glyph ids and placements and carries the STRIDE of each. Goldberry fills
     * one in per fill, so every field here is written by Java -- which makes
     * this the layout row with the least margin for error in the table. A wrong
     * `size` offset reads a byte count as a glyph count.
     */
    GB_STRUCT(BLGlyphRun),
    GB_FIELD(BLGlyphRun, glyph_data),
    GB_FIELD(BLGlyphRun, placement_data),
    GB_FIELD(BLGlyphRun, size),
    GB_FIELD(BLGlyphRun, reserved),
    GB_FIELD(BLGlyphRun, placement_type),
    GB_FIELD(BLGlyphRun, glyph_advance),
    GB_FIELD(BLGlyphRun, placement_advance),
    GB_FIELD(BLGlyphRun, flags),

    /* Four int32s: an offset that moves the glyph and an advance that moves the
     * pen -- exactly the four numbers HarfBuzz reports per glyph, in the same
     * order. That correspondence is what makes the crossing a copy rather than
     * a conversion. */
    GB_STRUCT(BLGlyphPlacement),
    GB_FIELD(BLGlyphPlacement, placement),
    GB_FIELD(BLGlyphPlacement, advance),

    /* Scaled by the font's size, so ascent and descent are already in the units
     * a paint pass draws in. The union of ascent/v_ascent with
     * ascent_by_orientation[2] overlaps the same memory, which is why the Java
     * layout is a flat run of floats. */
    GB_STRUCT(BLFontMetrics),
    GB_FIELD(BLFontMetrics, size),
    GB_FIELD(BLFontMetrics, ascent),
    GB_FIELD(BLFontMetrics, v_ascent),
    GB_FIELD(BLFontMetrics, descent),
    GB_FIELD(BLFontMetrics, v_descent),
    GB_FIELD(BLFontMetrics, line_gap),
    GB_FIELD(BLFontMetrics, x_height),
    GB_FIELD(BLFontMetrics, cap_height),
    GB_FIELD(BLFontMetrics, x_min),
    GB_FIELD(BLFontMetrics, y_min),
    GB_FIELD(BLFontMetrics, x_max),
    GB_FIELD(BLFontMetrics, y_max),
    GB_FIELD(BLFontMetrics, underline_position),
    GB_FIELD(BLFontMetrics, underline_thickness),
    GB_FIELD(BLFontMetrics, strikethrough_position),
    GB_FIELD(BLFontMetrics, strikethrough_thickness),

    /*
     * Paths and strokes (ADR-0043).
     *
     * BLPathCore is BLObjectDetail-shaped like every other core object, so this
     * row says what BLImageCore's does: the Java side allocates one by this
     * size.
     *
     * The stroke enumerators matter more than they look. Both enumerate
     * positionally and neither is alphabetical -- BL_STROKE_JOIN_ROUND is 4 and
     * BL_STROKE_CAP_ROUND is 2, with a "reversed round" at 3 that no icon wants.
     * A Java constant that drifted from either would draw every icon in the set
     * with the wrong corners, on every platform at once, and return BL_SUCCESS.
     */
    GB_STRUCT(BLPathCore),

    GB_CONSTANT("BL_STROKE_CAP_BUTT", BL_STROKE_CAP_BUTT),
    GB_CONSTANT("BL_STROKE_CAP_SQUARE", BL_STROKE_CAP_SQUARE),
    GB_CONSTANT("BL_STROKE_CAP_ROUND", BL_STROKE_CAP_ROUND),

    GB_CONSTANT("BL_STROKE_JOIN_MITER_CLIP", BL_STROKE_JOIN_MITER_CLIP),
    GB_CONSTANT("BL_STROKE_JOIN_BEVEL", BL_STROKE_JOIN_BEVEL),
    GB_CONSTANT("BL_STROKE_JOIN_ROUND", BL_STROKE_JOIN_ROUND),

    /* Which Blend2D is linked in. A build fact, like SDL's version. */
    GB_STRUCT(BLRuntimeBuildInfo),
    GB_FIELD(BLRuntimeBuildInfo, major_version),
    GB_FIELD(BLRuntimeBuildInfo, minor_version),
    GB_FIELD(BLRuntimeBuildInfo, patch_version),
    GB_FIELD(BLRuntimeBuildInfo, build_type),
    GB_FIELD(BLRuntimeBuildInfo, baseline_cpu_features),
    GB_FIELD(BLRuntimeBuildInfo, supported_cpu_features),
    GB_FIELD(BLRuntimeBuildInfo, max_image_size),
    GB_FIELD(BLRuntimeBuildInfo, max_thread_count),
    GB_FIELD(BLRuntimeBuildInfo, compiler_info),

    /* Pixel formats. PRGB32 is the one that matters: premultiplied BGRA in
     * memory on a little-endian target, which is what PixelBuffer normalises to
     * and what a compositor expects. */
    GB_CONSTANT("BL_FORMAT_NONE", BL_FORMAT_NONE),
    GB_CONSTANT("BL_FORMAT_PRGB32", BL_FORMAT_PRGB32),
    GB_CONSTANT("BL_FORMAT_XRGB32", BL_FORMAT_XRGB32),
    GB_CONSTANT("BL_FORMAT_A8", BL_FORMAT_A8),

    /*
     * Result codes the bindings name. Blend2D exports no result-to-string
     * function -- blend2d-debug.h is header-only -- so the names live in Java,
     * which makes them exactly the kind of hard-coded constant that has to be
     * checked. The error range starts at 0x00010000, well outside a plausible
     * accidental value.
     */
    GB_CONSTANT("BL_SUCCESS", BL_SUCCESS),
    GB_CONSTANT("BL_ERROR_OUT_OF_MEMORY", BL_ERROR_OUT_OF_MEMORY),
    GB_CONSTANT("BL_ERROR_INVALID_VALUE", BL_ERROR_INVALID_VALUE),
    GB_CONSTANT("BL_ERROR_INVALID_STATE", BL_ERROR_INVALID_STATE),
    GB_CONSTANT("BL_ERROR_NOT_INITIALIZED", BL_ERROR_NOT_INITIALIZED),
    GB_CONSTANT("BL_ERROR_NOT_IMPLEMENTED", BL_ERROR_NOT_IMPLEMENTED),

    /* How the display scale reaches the rasterizer. */
    GB_CONSTANT("BL_TRANSFORM_OP_RESET", BL_TRANSFORM_OP_RESET),
    GB_CONSTANT("BL_TRANSFORM_OP_ASSIGN", BL_TRANSFORM_OP_ASSIGN),
    GB_CONSTANT("BL_TRANSFORM_OP_TRANSLATE", BL_TRANSFORM_OP_TRANSLATE),
    GB_CONSTANT("BL_TRANSFORM_OP_SCALE", BL_TRANSFORM_OP_SCALE),

    /* SRC_COPY overwrites rather than blends -- what clearing a frame means. */
    GB_CONSTANT("BL_COMP_OP_SRC_OVER", BL_COMP_OP_SRC_OVER),
    GB_CONSTANT("BL_COMP_OP_SRC_COPY", BL_COMP_OP_SRC_COPY),

    /* Blend2D must be allowed to write the buffer it was handed. A bit set, so
     * these values do not shift if Blend2D adds one. */
    GB_CONSTANT("BL_DATA_ACCESS_NO_FLAGS", BL_DATA_ACCESS_NO_FLAGS),
    GB_CONSTANT("BL_DATA_ACCESS_READ", BL_DATA_ACCESS_READ),
    GB_CONSTANT("BL_DATA_ACCESS_WRITE", BL_DATA_ACCESS_WRITE),
    GB_CONSTANT("BL_DATA_ACCESS_RW", BL_DATA_ACCESS_RW),

    /*
     * How a glyph run's placements are to be read. ADVANCE_OFFSET is the one
     * Goldberry uses and the one that decides the units: Blend2D multiplies
     * those placements by the FONT MATRIX, which is size/units-per-em, so they
     * have to arrive in font design units. Naming DESIGN_UNITS and USER_UNITS
     * beside it is not decoration -- picking either of them by mistake would
     * scale every advance by the point size and still render. See ADR-0034.
     */
    GB_CONSTANT("BL_GLYPH_PLACEMENT_TYPE_NONE", BL_GLYPH_PLACEMENT_TYPE_NONE),
    GB_CONSTANT("BL_GLYPH_PLACEMENT_TYPE_ADVANCE_OFFSET", BL_GLYPH_PLACEMENT_TYPE_ADVANCE_OFFSET),
    GB_CONSTANT("BL_GLYPH_PLACEMENT_TYPE_DESIGN_UNITS", BL_GLYPH_PLACEMENT_TYPE_DESIGN_UNITS),
    GB_CONSTANT("BL_GLYPH_PLACEMENT_TYPE_USER_UNITS", BL_GLYPH_PLACEMENT_TYPE_USER_UNITS),
    GB_CONSTANT("BL_GLYPH_PLACEMENT_TYPE_ABSOLUTE_UNITS", BL_GLYPH_PLACEMENT_TYPE_ABSOLUTE_UNITS),

    GB_CONSTANT("BL_RUNTIME_INFO_TYPE_BUILD", BL_RUNTIME_INFO_TYPE_BUILD),
    GB_CONSTANT("BL_RUNTIME_INFO_TYPE_SYSTEM", BL_RUNTIME_INFO_TYPE_SYSTEM),
    GB_CONSTANT("BL_RUNTIME_INFO_TYPE_RESOURCE", BL_RUNTIME_INFO_TYPE_RESOURCE),

    /*
     * HarfBuzz (ADR-0032).
     *
     * Shaping hands back two parallel arrays that Goldberry reads in place, so
     * these two strides are load-bearing in a way most layout rows are not: get
     * either wrong and glyph 0 is fine while every glyph after it is read from
     * the middle of its neighbour. Both structs carry private `var` members that
     * are part of the stride and must never be read, which is exactly why the
     * size is registered rather than assumed from the public fields.
     */
    GB_STRUCT(hb_glyph_info_t),
    GB_FIELD(hb_glyph_info_t, codepoint),
    GB_FIELD(hb_glyph_info_t, cluster),

    GB_STRUCT(hb_glyph_position_t),
    GB_FIELD(hb_glyph_position_t, x_advance),
    GB_FIELD(hb_glyph_position_t, y_advance),
    GB_FIELD(hb_glyph_position_t, x_offset),
    GB_FIELD(hb_glyph_position_t, y_offset),

    /* Text direction. Not sequential -- LTR is 4, and the gap below it is why
     * the values are declared rather than counted. */
    GB_CONSTANT("HB_DIRECTION_INVALID", HB_DIRECTION_INVALID),
    GB_CONSTANT("HB_DIRECTION_LTR", HB_DIRECTION_LTR),
    GB_CONSTANT("HB_DIRECTION_RTL", HB_DIRECTION_RTL),
    GB_CONSTANT("HB_DIRECTION_TTB", HB_DIRECTION_TTB),
    GB_CONSTANT("HB_DIRECTION_BTT", HB_DIRECTION_BTT),

    /* How HarfBuzz may treat a font's bytes. */
    GB_CONSTANT("HB_MEMORY_MODE_DUPLICATE", HB_MEMORY_MODE_DUPLICATE),
    GB_CONSTANT("HB_MEMORY_MODE_READONLY", HB_MEMORY_MODE_READONLY),
};

GOLDBERRY_EXPORT const goldberry_layout_entry_t *goldberry_layout_table(void) {
    return GOLDBERRY_LAYOUTS;
}

GOLDBERRY_EXPORT uint32_t goldberry_layout_count(void) {
    return (uint32_t) (sizeof(GOLDBERRY_LAYOUTS) / sizeof(GOLDBERRY_LAYOUTS[0]));
}

/* ------------------------------------------------------------------------ */
/* Measure callback probe                                                   */
/* ------------------------------------------------------------------------ */

/*
 * Calls a YGMeasureFunc and reports what came back.
 *
 * Yoga's measure callback returns YGSize BY VALUE, which is the fiddliest thing
 * Goldberry asks of FFM and the one place where a mistake is invisible in Java:
 * two floats returned by value travel in registers, and each target disagrees
 * about which. On SysV x86-64 they pack into XMM0; on AArch64 they are a
 * homogeneous float aggregate in s0/s1; on Win64 the pair is folded into RAX.
 * Java can build an upcall stub that *looks* right on all three and be wrong on
 * two of them, and Yoga would read the corruption as a layout, not an error.
 *
 * So the check has to come from C. This function is called from Java with an
 * upcall stub, and it hands back what the C compiler for this target actually
 * received.
 *
 * The results leave through out-parameters rather than as a returned YGSize on
 * purpose. Returning one would put a struct-by-value DOWNCALL return in the same
 * test, and a failure could then be either mechanism. Out-parameters keep the
 * upcall's return the only struct crossing the boundary.
 *
 * The mode arguments are `int` rather than YGMeasureMode so the Java descriptor
 * can say JAVA_INT without depending on how the compiler sized the enum. The
 * callback itself still receives YGMeasureMode exactly as Yoga declares it,
 * which is the signature under test.
 */
GOLDBERRY_EXPORT void goldberry_probe_measure(YGMeasureFunc measure,
                                              float width, int width_mode,
                                              float height, int height_mode,
                                              float *out_width, float *out_height) {
    YGSize size;

    /* Called from a language that can pass null. A segfault here would take the
     * JVM with it and report nothing useful. */
    if (measure == NULL || out_width == NULL || out_height == NULL) {
        return;
    }

    size = measure(NULL, width, (YGMeasureMode) width_mode, height, (YGMeasureMode) height_mode);
    *out_width = size.width;
    *out_height = size.height;
}
