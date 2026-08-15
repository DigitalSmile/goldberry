/*
 * libgoldberry translation unit.
 *
 * Deliberately tiny. Goldberry binds its native dependencies through
 * hand-written FFM downcalls (ADR-0010), not through C glue, so this file holds
 * only the two things that cannot live on the Java side:
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
 * See docs/ARCHITECTURE.md §3.1 and §3.2.
 */

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#define GOLDBERRY_EXPORT __declspec(dllexport)
#else
#define GOLDBERRY_EXPORT __attribute__((visibility("default")))
#endif

/* Bumped whenever the exported surface changes shape. */
#define GOLDBERRY_ABI_VERSION 1u

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
     * Upstream structs are registered here as they are bound -- SDL_Event and
     * YGSize first, for the M0 window and the measure callback. A struct bound
     * in Java but absent here fails the verification test, which is the point.
     */
};

GOLDBERRY_EXPORT const goldberry_layout_entry_t *goldberry_layout_table(void) {
    return GOLDBERRY_LAYOUTS;
}

GOLDBERRY_EXPORT uint32_t goldberry_layout_count(void) {
    return (uint32_t) (sizeof(GOLDBERRY_LAYOUTS) / sizeof(GOLDBERRY_LAYOUTS[0]));
}
