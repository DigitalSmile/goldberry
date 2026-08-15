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

#if defined(_WIN32)
#define GOLDBERRY_EXPORT __declspec(dllexport)
#else
#define GOLDBERRY_EXPORT __attribute__((visibility("default")))
#endif

/* Bumped whenever the exported surface changes shape. */
#define GOLDBERRY_ABI_VERSION 4u

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

    GB_STRUCT(SDL_Surface),
    GB_FIELD(SDL_Surface, flags),
    GB_FIELD(SDL_Surface, format),
    GB_FIELD(SDL_Surface, w),
    GB_FIELD(SDL_Surface, h),
    GB_FIELD(SDL_Surface, pitch),
    GB_FIELD(SDL_Surface, pixels),
    GB_FIELD(SDL_Surface, refcount),
    GB_FIELD(SDL_Surface, reserved),

    GB_STRUCT(SDL_Rect),
    GB_FIELD(SDL_Rect, x),
    GB_FIELD(SDL_Rect, y),
    GB_FIELD(SDL_Rect, w),
    GB_FIELD(SDL_Rect, h),

    /* Event types Goldberry dispatches on. */
    GB_CONSTANT("SDL_EVENT_QUIT", SDL_EVENT_QUIT),
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

    /* Surface formats the CPU present path accepts. */
    GB_CONSTANT("SDL_PIXELFORMAT_XRGB8888", SDL_PIXELFORMAT_XRGB8888),
    GB_CONSTANT("SDL_PIXELFORMAT_ARGB8888", SDL_PIXELFORMAT_ARGB8888),
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
