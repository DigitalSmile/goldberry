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

#if defined(_WIN32)
#define GOLDBERRY_EXPORT __declspec(dllexport)
#else
#define GOLDBERRY_EXPORT __attribute__((visibility("default")))
#endif

/* Bumped whenever the exported surface changes shape. */
#define GOLDBERRY_ABI_VERSION 2u

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
