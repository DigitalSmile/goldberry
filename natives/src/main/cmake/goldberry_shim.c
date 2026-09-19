/*
 * libgoldberry translation unit.
 *
 * Deliberately tiny. Goldberry binds its native dependencies through
 * hand-written FFM downcalls (ADR-0010), not through C glue, so this file holds
 * only the four things that cannot live on the Java side:
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
 *   4. Markdown's event stream, encoded into one buffer. md4c is a SAX parser and
 *      binding it the obvious way would cross the boundary thousands of times per
 *      document; the hot path does not cross FFM (ADR-0190, ADR-0294).
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

/*
 * For md4c's parser, and for the HTML5 named-entity table beside it. Both are
 * compiled into this translation unit's target rather than linked as a library
 * (see the CMakeLists), because the only caller of either is the code at the
 * bottom of this file. See ADR-0294.
 */
#include <md4c.h>
#include <entity.h>

/*
 * For WebPData, WebPAnimDecoderOptions, WebPAnimInfo and WEBP_DEMUX_ABI_VERSION.
 *
 * Nothing here calls libwebp -- the bindings reach it directly through FFM -- so
 * these headers are included for the table alone. The animation decoder is the
 * one place in this module where Java ALLOCATES an upstream struct and writes
 * into it by offset: a WebPData holding a pointer and a size, an options block
 * the library fills in, and an info block it reads back. Those three sizes and
 * offsets were counted by hand against demux.h and believed, which is precisely
 * what ADR-0010 says not to do. And WEBP_DEMUX_ABI_VERSION travels on every
 * `…Internal` call: a pinned libwebp that bumps it makes the decoder refuse
 * every animation, silently, because "not an animation" is a normal answer.
 */
#include <webp/decode.h>
#include <webp/demux.h>
#include <webp/mux_types.h>

/* malloc/realloc/free and memcpy/memset, for the Markdown event buffer. */
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#define GOLDBERRY_EXPORT __declspec(dllexport)
#else
#define GOLDBERRY_EXPORT __attribute__((visibility("default")))
#endif

/* Bumped whenever the exported surface changes shape. */
#define GOLDBERRY_ABI_VERSION 14u

GOLDBERRY_EXPORT uint32_t goldberry_abi_version(void) {
    return GOLDBERRY_ABI_VERSION;
}

/* ------------------------------------------------------------------------ */
/* Platform capabilities -- what THIS build can ask the desktop             */
/* ------------------------------------------------------------------------ */

/*
 * The fifth thing that cannot live on the Java side, and the newest: what the
 * platform layer in this particular libgoldberry can actually do.
 *
 * An API is not a capability. SDL_GetSystemTheme() is declared on every
 * platform and compiled into every build of SDL, and on Linux its whole
 * implementation is behind SDL_USE_LIBDBUS -- which SDL's CMake #defines only
 * when the D-Bus *headers* were present on the machine that compiled it. A build
 * made without libdbus-1-dev therefore returns SDL_SYSTEM_THEME_UNKNOWN on a
 * desktop that is set to dark, forever, with no error anywhere: not on the
 * desktop, not in the build log, not at run time. The same probe gates the XDG
 * portal file dialog and the screensaver inhibit; a second one gates the input
 * method on X11, and a third input-device hotplug.
 *
 * Three shipped capabilities silently downgraded by an absent -dev package is
 * what docs/gaps.md G32 is, and half its answer is that the build now refuses to
 * produce such a library by accident (CMakeLists.txt). This is the other half:
 * whatever a build ends up being, it says so, in one word an application can
 * read back through Goldberry.capabilities() (ADR-0325).
 *
 * Build-time, deliberately. These bits describe the LIBRARY, not the session it
 * is loaded into: a build with D-Bus support running on a desktop that has no
 * colour-scheme setting still reports SYSTEM_THEME, because it can ask and the
 * desktop is what declined to answer. "Could not ask" and "asked and was told
 * nothing" are different facts and only the first one is fixable.
 */
#define GOLDBERRY_CAP_SYSTEM_THEME 0x1u
#define GOLDBERRY_CAP_INPUT_METHOD 0x2u
#define GOLDBERRY_CAP_DEVICE_HOTPLUG 0x4u
#define GOLDBERRY_CAP_FILE_DIALOG 0x8u
#define GOLDBERRY_CAP_SCREENSAVER_INHIBIT 0x10u
#define GOLDBERRY_CAP_WINDOW_DECORATIONS 0x20u
#define GOLDBERRY_CAP_WAYLAND 0x40u

/*
 * GOLDBERRY_PLATFORM_DBUS, _IBUS and _UDEV are passed by the superbuild, which
 * probes for exactly the pkg-config modules SDL's own CMake probes for and
 * cross-checks its answer against the SDL_build_config.h SDL generated. They are
 * absent rather than 0 when the probe failed, hence #ifdef.
 */
#if defined(__linux__)

#if defined(GOLDBERRY_PLATFORM_DBUS)
/* SDL_system_theme.c, SDL_portaldialog.c and SDL_dbus.c's screensaver inhibit
 * are one file set behind one #define. */
#define GOLDBERRY_CAPS_DBUS \
    (GOLDBERRY_CAP_SYSTEM_THEME | GOLDBERRY_CAP_FILE_DIALOG | GOLDBERRY_CAP_SCREENSAVER_INHIBIT)
#else
#define GOLDBERRY_CAPS_DBUS 0u
#endif

/*
 * The X11 input method only. SDL drives zwp_text_input_v3 from the compositor on
 * Wayland and needs neither IBus nor Fcitx there, so a build without these
 * headers composes perfectly well on a Wayland session and not at all on an X11
 * one. A build-time bit cannot express "depends on the session", so it reports
 * what it is: whether an X11 session would have an input method.
 */
#if defined(GOLDBERRY_PLATFORM_IBUS)
#define GOLDBERRY_CAPS_IBUS GOLDBERRY_CAP_INPUT_METHOD
#else
#define GOLDBERRY_CAPS_IBUS 0u
#endif

#if defined(GOLDBERRY_PLATFORM_UDEV)
#define GOLDBERRY_CAPS_UDEV GOLDBERRY_CAP_DEVICE_HOTPLUG
#else
#define GOLDBERRY_CAPS_UDEV 0u
#endif

/*
 * These two come from a different place than the three above: the superbuild
 * reads them out of the SDL_build_config.h SDL generated, rather than predicting
 * them with a pkg-config probe and confirming them there. SDL decides its Wayland
 * driver with one pkg_check_modules over five specs plus a scanner binary, and a
 * prediction narrower than that would be worse than none (ADR-0422).
 *
 * A window's decorations are a build-time fact on Linux and only on Linux:
 * without libdecor SDL compiles no client-side decoration support at all, so a
 * Wayland window opens bare however the session is configured (ADR-0083). The
 * plugin that then has to load is a *run-time* matter and a separate defect
 * (ADR-0084) -- this bit says the toolkit was built able to ask for a titlebar,
 * not that one will appear.
 */
#if defined(GOLDBERRY_PLATFORM_HAVE_LIBDECOR_H)
#define GOLDBERRY_CAPS_LIBDECOR GOLDBERRY_CAP_WINDOW_DECORATIONS
#else
#define GOLDBERRY_CAPS_LIBDECOR 0u
#endif

#if defined(GOLDBERRY_PLATFORM_SDL_VIDEO_DRIVER_WAYLAND)
#define GOLDBERRY_CAPS_WAYLAND GOLDBERRY_CAP_WAYLAND
#else
#define GOLDBERRY_CAPS_WAYLAND 0u
#endif

#define GOLDBERRY_CAPABILITIES \
    (GOLDBERRY_CAPS_DBUS | GOLDBERRY_CAPS_IBUS | GOLDBERRY_CAPS_UDEV | GOLDBERRY_CAPS_LIBDECOR \
     | GOLDBERRY_CAPS_WAYLAND)

#else

/*
 * macOS and Windows have no such probe and no such failure mode: every one of
 * these is implemented against a system framework that is part of the SDK SDL is
 * compiled with, not against an optional third-party header that may or may not
 * be installed. NSUserDefaults and the AppsUseLightTheme registry value answer
 * the theme, NSTextInputClient and TSF are the input methods, IOKit and
 * WM_DEVICECHANGE are hotplug, NSOpenPanel and IFileDialog are the file dialogs,
 * IOPMAssertion and SetThreadExecutionState are the screensaver.
 *
 * Window decorations are on that list: the window server draws them, there is no
 * optional library in the way, and there is nothing a build could have compiled
 * out. GOLDBERRY_CAP_WAYLAND is not, and its absence here is a statement rather
 * than an omission -- there is no Wayland on either platform, so a library that
 * claimed the bit would be claiming something false about the session it will run
 * in (ADR-0422).
 */
#define GOLDBERRY_CAPABILITIES \
    (GOLDBERRY_CAP_SYSTEM_THEME | GOLDBERRY_CAP_INPUT_METHOD | GOLDBERRY_CAP_DEVICE_HOTPLUG \
     | GOLDBERRY_CAP_FILE_DIALOG | GOLDBERRY_CAP_SCREENSAVER_INHIBIT \
     | GOLDBERRY_CAP_WINDOW_DECORATIONS)

#endif

GOLDBERRY_EXPORT uint32_t goldberry_platform_capabilities(void) {
    return GOLDBERRY_CAPABILITIES;
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

    GB_STRUCT(SDL_TextEditingEvent),
    GB_FIELD(SDL_TextEditingEvent, type),
    GB_FIELD(SDL_TextEditingEvent, timestamp),
    GB_FIELD(SDL_TextEditingEvent, windowID),
    GB_FIELD(SDL_TextEditingEvent, text),
    GB_FIELD(SDL_TextEditingEvent, start),
    GB_FIELD(SDL_TextEditingEvent, length),

    /* Files dropped on a window (sec. G35b, ADR-0330). The two floats between
     * `windowID` and the two pointers are what make this worth probing: the
     * compiler pads four bytes before `source` to align it, and a layout that
     * counted by hand would read the dropped path out of the middle of a
     * pointer. */
    GB_STRUCT(SDL_DropEvent),
    GB_FIELD(SDL_DropEvent, type),
    GB_FIELD(SDL_DropEvent, timestamp),
    GB_FIELD(SDL_DropEvent, windowID),
    GB_FIELD(SDL_DropEvent, x),
    GB_FIELD(SDL_DropEvent, y),
    GB_FIELD(SDL_DropEvent, source),
    GB_FIELD(SDL_DropEvent, data),

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
    GB_CONSTANT("SDL_EVENT_TEXT_EDITING", SDL_EVENT_TEXT_EDITING),
    GB_CONSTANT("SDL_EVENT_MOUSE_MOTION", SDL_EVENT_MOUSE_MOTION),
    GB_CONSTANT("SDL_EVENT_MOUSE_BUTTON_DOWN", SDL_EVENT_MOUSE_BUTTON_DOWN),
    GB_CONSTANT("SDL_EVENT_MOUSE_BUTTON_UP", SDL_EVENT_MOUSE_BUTTON_UP),
    GB_CONSTANT("SDL_EVENT_MOUSE_WHEEL", SDL_EVENT_MOUSE_WHEEL),
    GB_CONSTANT("SDL_EVENT_WINDOW_EXPOSED", SDL_EVENT_WINDOW_EXPOSED),
    GB_CONSTANT("SDL_EVENT_WINDOW_MOVED", SDL_EVENT_WINDOW_MOVED),
    GB_CONSTANT("SDL_EVENT_WINDOW_RESIZED", SDL_EVENT_WINDOW_RESIZED),
    GB_CONSTANT("SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED", SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED),
    GB_CONSTANT("SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED", SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED),
    GB_CONSTANT("SDL_EVENT_WINDOW_FOCUS_GAINED", SDL_EVENT_WINDOW_FOCUS_GAINED),
    GB_CONSTANT("SDL_EVENT_WINDOW_MAXIMIZED", SDL_EVENT_WINDOW_MAXIMIZED),
    GB_CONSTANT("SDL_EVENT_WINDOW_RESTORED", SDL_EVENT_WINDOW_RESTORED),
    GB_CONSTANT("SDL_EVENT_WINDOW_FOCUS_LOST", SDL_EVENT_WINDOW_FOCUS_LOST),
    GB_CONSTANT("SDL_EVENT_WINDOW_CLOSE_REQUESTED", SDL_EVENT_WINDOW_CLOSE_REQUESTED),
    GB_CONSTANT("SDL_EVENT_SYSTEM_THEME_CHANGED", SDL_EVENT_SYSTEM_THEME_CHANGED),
    GB_CONSTANT("SDL_EVENT_DROP_FILE", SDL_EVENT_DROP_FILE),
    GB_CONSTANT("SDL_EVENT_DROP_TEXT", SDL_EVENT_DROP_TEXT),
    GB_CONSTANT("SDL_EVENT_DROP_POSITION", SDL_EVENT_DROP_POSITION),
    GB_CONSTANT("SDL_EVENT_DROP_COMPLETE", SDL_EVENT_DROP_COMPLETE),
    GB_CONSTANT("SDL_EVENT_DROP_BEGIN", SDL_EVENT_DROP_BEGIN),
    GB_CONSTANT("SDL_EVENT_USER", SDL_EVENT_USER),

    /* The desktop's light-or-dark setting (sec. G26, ADR-0322). Ordinals in a C
     * enum, and a wrong one starts the application in the wrong theme with no
     * error anywhere -- which is exactly the failure this table exists for. */
    GB_CONSTANT("SDL_SYSTEM_THEME_UNKNOWN", SDL_SYSTEM_THEME_UNKNOWN),
    GB_CONSTANT("SDL_SYSTEM_THEME_LIGHT", SDL_SYSTEM_THEME_LIGHT),
    GB_CONSTANT("SDL_SYSTEM_THEME_DARK", SDL_SYSTEM_THEME_DARK),

    /* Window creation flags. */
    GB_CONSTANT("SDL_WINDOW_RESIZABLE", SDL_WINDOW_RESIZABLE),
    GB_CONSTANT("SDL_WINDOW_MAXIMIZED", SDL_WINDOW_MAXIMIZED),
    GB_CONSTANT("SDL_WINDOW_BORDERLESS", SDL_WINDOW_BORDERLESS),
    GB_CONSTANT("SDL_WINDOW_HIGH_PIXEL_DENSITY", SDL_WINDOW_HIGH_PIXEL_DENSITY),
    GB_CONSTANT("SDL_WINDOW_HIDDEN", SDL_WINDOW_HIDDEN),
    GB_CONSTANT("SDL_WINDOW_POPUP_MENU", SDL_WINDOW_POPUP_MENU),
    GB_CONSTANT("SDL_WINDOW_TOOLTIP", SDL_WINDOW_TOOLTIP),
    GB_CONSTANT("SDL_WINDOW_NOT_FOCUSABLE", SDL_WINDOW_NOT_FOCUSABLE),
    GB_CONSTANT("SDL_WINDOW_TRANSPARENT", SDL_WINDOW_TRANSPARENT),

    /*
     * Tray entry flags (sec. 9's `tray-icon`). Exactly one of BUTTON, CHECKBOX
     * and SUBMENU is mandatory per entry, and the two optional ones live in the
     * high bits -- DISABLED is 0x80000000, which is a negative `int` in Java and
     * is exactly the kind of value a hand-copied constant gets wrong quietly.
     */
    GB_CONSTANT("SDL_TRAYENTRY_BUTTON", SDL_TRAYENTRY_BUTTON),
    GB_CONSTANT("SDL_TRAYENTRY_CHECKBOX", SDL_TRAYENTRY_CHECKBOX),
    GB_CONSTANT("SDL_TRAYENTRY_SUBMENU", SDL_TRAYENTRY_SUBMENU),
    GB_CONSTANT("SDL_TRAYENTRY_DISABLED", SDL_TRAYENTRY_DISABLED),
    GB_CONSTANT("SDL_TRAYENTRY_CHECKED", SDL_TRAYENTRY_CHECKED),

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
     * Gradients (ADR-0207). BLGradientCore is BLObjectDetail-shaped like every
     * other core object; BLLinearGradientValues is the one that matters,
     * because it crosses bl_gradient_init_as as a `const void*` and nothing on
     * either side of that call checks its shape. Four doubles in the order a
     * caller writes them -- start point, then end point.
     */
    GB_STRUCT(BLGradientCore),
    GB_STRUCT(BLLinearGradientValues),
    GB_FIELD(BLLinearGradientValues, x0),
    GB_FIELD(BLLinearGradientValues, y0),
    GB_FIELD(BLLinearGradientValues, x1),
    GB_FIELD(BLLinearGradientValues, y1),

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

    /*
     * Which points a path encloses (ADR-0427). Two values, and the wrong one is
     * as silent as every other constant here: a shadow asked to cut its box out
     * of itself under NON_ZERO paints the hole solid instead, which is a dark
     * rectangle over the control and no error anywhere.
     */
    GB_CONSTANT("BL_FILL_RULE_NON_ZERO", BL_FILL_RULE_NON_ZERO),
    GB_CONSTANT("BL_FILL_RULE_EVEN_ODD", BL_FILL_RULE_EVEN_ODD),

    /*
     * How bl_image_scale resamples (ADR-0428). Positional enumerators, so a
     * value inserted upstream shifts every one after it -- and the failure is a
     * thumbnail resampled by the wrong filter, which looks like a thumbnail.
     * BL_IMAGE_SCALE_FILTER_NONE is deliberately absent: it is the absence of a
     * filter rather than one of them, and nothing binds it.
     */
    GB_CONSTANT("BL_IMAGE_SCALE_FILTER_NEAREST", BL_IMAGE_SCALE_FILTER_NEAREST),
    GB_CONSTANT("BL_IMAGE_SCALE_FILTER_BILINEAR", BL_IMAGE_SCALE_FILTER_BILINEAR),
    GB_CONSTANT("BL_IMAGE_SCALE_FILTER_BICUBIC", BL_IMAGE_SCALE_FILTER_BICUBIC),
    GB_CONSTANT("BL_IMAGE_SCALE_FILTER_LANCZOS", BL_IMAGE_SCALE_FILTER_LANCZOS),

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

    /*
     * Gradients (ADR-0207). Two enumerators, and both are the zero of their
     * enum -- which is exactly why they are checked: a zero that happens to be
     * right today is indistinguishable from a field nobody wrote, and
     * BL_GRADIENT_TYPE_LINEAR sitting at 0 is the reason a wrong `values`
     * pointer would still produce a gradient rather than an error.
     */
    GB_CONSTANT("BL_GRADIENT_TYPE_LINEAR", BL_GRADIENT_TYPE_LINEAR),
    GB_CONSTANT("BL_GRADIENT_TYPE_RADIAL", BL_GRADIENT_TYPE_RADIAL),
    GB_CONSTANT("BL_GRADIENT_TYPE_CONIC", BL_GRADIENT_TYPE_CONIC),

    /* What happens outside the two stops. PAD holds the end colours, which is
     * what a fade under a chart's band wants and what CSS specifies. */
    GB_CONSTANT("BL_EXTEND_MODE_PAD", BL_EXTEND_MODE_PAD),
    GB_CONSTANT("BL_EXTEND_MODE_REPEAT", BL_EXTEND_MODE_REPEAT),
    GB_CONSTANT("BL_EXTEND_MODE_REFLECT", BL_EXTEND_MODE_REFLECT),

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

    /*
     * md4c (ADR-0294).
     *
     * The Java side switches on every one of these while decoding the event
     * buffer, and md4c has already inserted a value into the middle of two of
     * these enums between releases -- MD_TEXT_NULLCHAR and MD_SPAN_LATEXMATH were
     * both additions. A stream decoded against a shifted ordinal is not a crash:
     * it is a document whose headings render as block quotes.
     *
     * No struct rows. Every detail struct is read HERE, by the code the same
     * compiler built, and reaches Java as three integers in a record that has no
     * layout to get wrong -- which is the whole argument of ADR-0294 stated as a
     * gap in this table.
     */
    GB_CONSTANT("MD_BLOCK_DOC", MD_BLOCK_DOC),
    GB_CONSTANT("MD_BLOCK_QUOTE", MD_BLOCK_QUOTE),
    GB_CONSTANT("MD_BLOCK_UL", MD_BLOCK_UL),
    GB_CONSTANT("MD_BLOCK_OL", MD_BLOCK_OL),
    GB_CONSTANT("MD_BLOCK_LI", MD_BLOCK_LI),
    GB_CONSTANT("MD_BLOCK_HR", MD_BLOCK_HR),
    GB_CONSTANT("MD_BLOCK_H", MD_BLOCK_H),
    GB_CONSTANT("MD_BLOCK_CODE", MD_BLOCK_CODE),
    GB_CONSTANT("MD_BLOCK_HTML", MD_BLOCK_HTML),
    GB_CONSTANT("MD_BLOCK_P", MD_BLOCK_P),
    GB_CONSTANT("MD_BLOCK_TABLE", MD_BLOCK_TABLE),
    GB_CONSTANT("MD_BLOCK_THEAD", MD_BLOCK_THEAD),
    GB_CONSTANT("MD_BLOCK_TBODY", MD_BLOCK_TBODY),
    GB_CONSTANT("MD_BLOCK_TR", MD_BLOCK_TR),
    GB_CONSTANT("MD_BLOCK_TH", MD_BLOCK_TH),
    GB_CONSTANT("MD_BLOCK_TD", MD_BLOCK_TD),

    GB_CONSTANT("MD_SPAN_EM", MD_SPAN_EM),
    GB_CONSTANT("MD_SPAN_STRONG", MD_SPAN_STRONG),
    GB_CONSTANT("MD_SPAN_A", MD_SPAN_A),
    GB_CONSTANT("MD_SPAN_IMG", MD_SPAN_IMG),
    GB_CONSTANT("MD_SPAN_CODE", MD_SPAN_CODE),
    GB_CONSTANT("MD_SPAN_DEL", MD_SPAN_DEL),
    GB_CONSTANT("MD_SPAN_LATEXMATH", MD_SPAN_LATEXMATH),
    GB_CONSTANT("MD_SPAN_LATEXMATH_DISPLAY", MD_SPAN_LATEXMATH_DISPLAY),
    GB_CONSTANT("MD_SPAN_WIKILINK", MD_SPAN_WIKILINK),
    GB_CONSTANT("MD_SPAN_U", MD_SPAN_U),

    GB_CONSTANT("MD_TEXT_NORMAL", MD_TEXT_NORMAL),
    GB_CONSTANT("MD_TEXT_NULLCHAR", MD_TEXT_NULLCHAR),
    GB_CONSTANT("MD_TEXT_BR", MD_TEXT_BR),
    GB_CONSTANT("MD_TEXT_SOFTBR", MD_TEXT_SOFTBR),
    GB_CONSTANT("MD_TEXT_ENTITY", MD_TEXT_ENTITY),
    GB_CONSTANT("MD_TEXT_CODE", MD_TEXT_CODE),
    GB_CONSTANT("MD_TEXT_HTML", MD_TEXT_HTML),
    GB_CONSTANT("MD_TEXT_LATEXMATH", MD_TEXT_LATEXMATH),

    GB_CONSTANT("MD_ALIGN_DEFAULT", MD_ALIGN_DEFAULT),
    GB_CONSTANT("MD_ALIGN_LEFT", MD_ALIGN_LEFT),
    GB_CONSTANT("MD_ALIGN_CENTER", MD_ALIGN_CENTER),
    GB_CONSTANT("MD_ALIGN_RIGHT", MD_ALIGN_RIGHT),

    /* The dialect bits. Flags rather than enumerators, so the wrong value is a
     * silently disabled extension: a table that renders as a paragraph of pipes. */
    GB_CONSTANT("MD_FLAG_COLLAPSEWHITESPACE", MD_FLAG_COLLAPSEWHITESPACE),
    GB_CONSTANT("MD_FLAG_PERMISSIVEATXHEADERS", MD_FLAG_PERMISSIVEATXHEADERS),
    GB_CONSTANT("MD_FLAG_PERMISSIVEURLAUTOLINKS", MD_FLAG_PERMISSIVEURLAUTOLINKS),
    GB_CONSTANT("MD_FLAG_PERMISSIVEEMAILAUTOLINKS", MD_FLAG_PERMISSIVEEMAILAUTOLINKS),
    GB_CONSTANT("MD_FLAG_PERMISSIVEWWWAUTOLINKS", MD_FLAG_PERMISSIVEWWWAUTOLINKS),
    GB_CONSTANT("MD_FLAG_NOINDENTEDCODEBLOCKS", MD_FLAG_NOINDENTEDCODEBLOCKS),
    GB_CONSTANT("MD_FLAG_NOHTMLBLOCKS", MD_FLAG_NOHTMLBLOCKS),
    GB_CONSTANT("MD_FLAG_NOHTMLSPANS", MD_FLAG_NOHTMLSPANS),
    GB_CONSTANT("MD_FLAG_TABLES", MD_FLAG_TABLES),
    GB_CONSTANT("MD_FLAG_STRIKETHROUGH", MD_FLAG_STRIKETHROUGH),
    GB_CONSTANT("MD_FLAG_TASKLISTS", MD_FLAG_TASKLISTS),
    GB_CONSTANT("MD_FLAG_LATEXMATHSPANS", MD_FLAG_LATEXMATHSPANS),
    GB_CONSTANT("MD_FLAG_WIKILINKS", MD_FLAG_WIKILINKS),
    GB_CONSTANT("MD_FLAG_UNDERLINE", MD_FLAG_UNDERLINE),
    GB_CONSTANT("MD_FLAG_HARD_SOFT_BREAKS", MD_FLAG_HARD_SOFT_BREAKS),

    /* The capability bits above. Not an upstream's constants but this library's
     * own, and on the table for the same reason every other row is: the Java
     * enum hard-codes each value, and a bit that disagrees reports the wrong
     * capability rather than failing. See ADR-0325. */
    GB_CONSTANT("GOLDBERRY_CAP_SYSTEM_THEME", GOLDBERRY_CAP_SYSTEM_THEME),
    GB_CONSTANT("GOLDBERRY_CAP_INPUT_METHOD", GOLDBERRY_CAP_INPUT_METHOD),
    GB_CONSTANT("GOLDBERRY_CAP_DEVICE_HOTPLUG", GOLDBERRY_CAP_DEVICE_HOTPLUG),
    GB_CONSTANT("GOLDBERRY_CAP_FILE_DIALOG", GOLDBERRY_CAP_FILE_DIALOG),
    GB_CONSTANT("GOLDBERRY_CAP_SCREENSAVER_INHIBIT", GOLDBERRY_CAP_SCREENSAVER_INHIBIT),
    GB_CONSTANT("GOLDBERRY_CAP_WINDOW_DECORATIONS", GOLDBERRY_CAP_WINDOW_DECORATIONS),
    GB_CONSTANT("GOLDBERRY_CAP_WAYLAND", GOLDBERRY_CAP_WAYLAND),

    /*
     * The subsystems SDL_Init takes. A bit mask rather than an enumeration, so
     * the Java enum spells each value out, and a wrong one is a subsystem that
     * never initializes and never says so: SDL_Init(0x20) with the wrong 0x20
     * returns true having started nothing.
     */
    GB_CONSTANT("SDL_INIT_AUDIO", SDL_INIT_AUDIO),
    GB_CONSTANT("SDL_INIT_VIDEO", SDL_INIT_VIDEO),
    GB_CONSTANT("SDL_INIT_JOYSTICK", SDL_INIT_JOYSTICK),
    GB_CONSTANT("SDL_INIT_HAPTIC", SDL_INIT_HAPTIC),
    GB_CONSTANT("SDL_INIT_GAMEPAD", SDL_INIT_GAMEPAD),
    GB_CONSTANT("SDL_INIT_EVENTS", SDL_INIT_EVENTS),
    GB_CONSTANT("SDL_INIT_SENSOR", SDL_INIT_SENSOR),
    GB_CONSTANT("SDL_INIT_CAMERA", SDL_INIT_CAMERA),

    /*
     * libwebp's animation decoder (ADR-0385).
     *
     * The three structs Java allocates and reads by offset. WebPData is a
     * pointer and a size_t, so its size is 12 on a 32-bit target and 16 here --
     * the ordinary reason a hand-counted layout is right on one machine only.
     * The two demux structs end in padding upstream reserved for later use,
     * which is exactly the field a future libwebp spends without saying so.
     */
    GB_STRUCT(WebPData),
    GB_FIELD(WebPData, bytes),
    GB_FIELD(WebPData, size),

    GB_STRUCT(WebPAnimDecoderOptions),
    GB_FIELD(WebPAnimDecoderOptions, color_mode),
    GB_FIELD(WebPAnimDecoderOptions, use_threads),
    GB_FIELD(WebPAnimDecoderOptions, padding),

    GB_STRUCT(WebPAnimInfo),
    GB_FIELD(WebPAnimInfo, canvas_width),
    GB_FIELD(WebPAnimInfo, canvas_height),
    GB_FIELD(WebPAnimInfo, loop_count),
    GB_FIELD(WebPAnimInfo, bgcolor),
    GB_FIELD(WebPAnimInfo, frame_count),
    GB_FIELD(WebPAnimInfo, pad),

    /* The version every `…Internal` entry point is called with. libwebp checks
     * it and refuses on a mismatch, and a refusal is indistinguishable from
     * "these bytes are not an animation". */
    GB_CONSTANT("WEBP_DEMUX_ABI_VERSION", WEBP_DEMUX_ABI_VERSION),
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

/* ------------------------------------------------------------------------ */
/* Markdown: one buffer instead of thousands of upcalls                     */
/* ------------------------------------------------------------------------ */

/*
 * md4c is a SAX parser: it calls back for every block, span and run of text in
 * the document. Bound the obvious way -- five upcall stubs written into an
 * MD_PARSER -- a thousand-word note would cross the FFM boundary several
 * thousand times, and each crossing would hand Java a detail struct whose layout
 * the Java side would then have to model. That is the shape ADR-0190 rules out
 * for every content module: the hot path does not cross FFM.
 *
 * So the event stream is *encoded here*, into one growable byte buffer, and Java
 * reads it once. Zero upcalls, no detail struct in the layout table, and the
 * parse is one downcall (ADR-0294).
 *
 * ## The wire format
 *
 * A sequence of records. Each is a 20-byte header, little-endian regardless of
 * the host -- the parse and the read are in one process, but an explicitly
 * spelled byte order is one fewer thing for a big-endian port to discover -- and
 * then `text_length` bytes of payload:
 *
 *     0   u8   event        1 enter block, 2 leave block, 3 enter span,
 *                           4 leave span, 5 text, 6 attribute
 *     1   u8   type         MD_BLOCKTYPE, MD_SPANTYPE or MD_TEXTTYPE
 *     2   u8   role         attribute records only: 1 primary, 2 secondary
 *     3   u8   flags        bit 0 tight, bit 1 task, bit 2 autolink
 *     4   u32  a            level / start / mark / align / column count
 *     8   u32  b            mark delimiter / task mark offset / head rows
 *     12  u32  c            body rows / fence character
 *     16  u32  text_length  bytes of payload following this header
 *
 * An **attribute** is md4c's MD_ATTRIBUTE: a string broken into substrings, each
 * with a text type of its own, because `[a](x?y&amp;z)` has an entity inside a
 * URL and whoever writes the HTML has to know which part is which. Its records
 * are emitted BEFORE the enter record they belong to, so the reader accumulates
 * them and the enter event consumes them. `role` says which attribute of the two
 * a part belongs to: href/src/info/target is 1, title/lang is 2.
 *
 * The three numeric columns carry whatever the block's own detail struct has;
 * `MarkdownStream` on the Java side is the other half of this table and names
 * each of them per block type.
 */

#define GB_MD_ENTER_BLOCK  1u
#define GB_MD_LEAVE_BLOCK  2u
#define GB_MD_ENTER_SPAN   3u
#define GB_MD_LEAVE_SPAN   4u
#define GB_MD_TEXT         5u
#define GB_MD_ATTRIBUTE    6u

#define GB_MD_ROLE_NONE      0u
#define GB_MD_ROLE_PRIMARY   1u
#define GB_MD_ROLE_SECONDARY 2u

#define GB_MD_FLAG_TIGHT    0x01u
#define GB_MD_FLAG_TASK     0x02u
#define GB_MD_FLAG_AUTOLINK 0x04u

#define GB_MD_HEADER_SIZE 20u

typedef struct {
    unsigned char *bytes;
    size_t length;
    size_t capacity;
    /* Set once and never cleared: an allocation that failed part way through
     * leaves a truncated stream, which must not be handed to Java as a document.
     * Every callback checks it and aborts the parse. */
    int failed;
} goldberry_md_stream_t;

static int goldberry_md_reserve(goldberry_md_stream_t *stream, size_t extra) {
    size_t needed;
    size_t capacity;
    unsigned char *grown;

    if (stream->failed) {
        return 0;
    }
    needed = stream->length + extra;
    if (needed < stream->length) { /* size_t overflow -- a document this big is not a document */
        stream->failed = 1;
        return 0;
    }
    if (needed <= stream->capacity) {
        return 1;
    }
    capacity = stream->capacity == 0 ? 4096u : stream->capacity;
    while (capacity < needed) {
        if (capacity > (size_t) -1 / 2u) {
            stream->failed = 1;
            return 0;
        }
        capacity *= 2u;
    }
    grown = (unsigned char *) realloc(stream->bytes, capacity);
    if (grown == NULL) {
        stream->failed = 1;
        return 0;
    }
    stream->bytes = grown;
    stream->capacity = capacity;
    return 1;
}

static void goldberry_md_u32(unsigned char *at, uint32_t value) {
    at[0] = (unsigned char) (value & 0xffu);
    at[1] = (unsigned char) ((value >> 8) & 0xffu);
    at[2] = (unsigned char) ((value >> 16) & 0xffu);
    at[3] = (unsigned char) ((value >> 24) & 0xffu);
}

/*
 * One record. `text` may be NULL when `text_length` is zero.
 *
 * Returns zero when the buffer could not grow, which every caller passes
 * straight back to md4c as "abort".
 */
static int goldberry_md_record(goldberry_md_stream_t *stream,
                               unsigned event, unsigned type, unsigned role, unsigned flags,
                               uint32_t a, uint32_t b, uint32_t c,
                               const char *text, uint32_t text_length) {
    unsigned char *at;

    if (!goldberry_md_reserve(stream, GB_MD_HEADER_SIZE + (size_t) text_length)) {
        return 0;
    }
    at = stream->bytes + stream->length;
    at[0] = (unsigned char) event;
    at[1] = (unsigned char) type;
    at[2] = (unsigned char) role;
    at[3] = (unsigned char) flags;
    goldberry_md_u32(at + 4, a);
    goldberry_md_u32(at + 8, b);
    goldberry_md_u32(at + 12, c);
    goldberry_md_u32(at + 16, text_length);
    if (text_length > 0u && text != NULL) {
        memcpy(at + GB_MD_HEADER_SIZE, text, (size_t) text_length);
    }
    stream->length += GB_MD_HEADER_SIZE + (size_t) text_length;
    return 1;
}

/*
 * An MD_ATTRIBUTE, as one record per substring.
 *
 * md4c terminates `substr_offsets` with an entry equal to the attribute's size,
 * which is what bounds this loop -- the same walk md4c-html.c makes. An absent
 * attribute has a NULL text and emits nothing, so a link with no title is a link
 * whose secondary attribute never appears rather than one with an empty string.
 */
static int goldberry_md_attribute(goldberry_md_stream_t *stream, unsigned role, const MD_ATTRIBUTE *attribute) {
    unsigned i;

    if (attribute == NULL || attribute->text == NULL || attribute->substr_offsets == NULL) {
        return 1;
    }
    for (i = 0; attribute->substr_offsets[i] < attribute->size; i++) {
        MD_OFFSET offset = attribute->substr_offsets[i];
        MD_SIZE size = attribute->substr_offsets[i + 1] - offset;
        if (!goldberry_md_record(stream, GB_MD_ATTRIBUTE, (unsigned) attribute->substr_types[i], role, 0u,
                                 0u, 0u, 0u, attribute->text + offset, (uint32_t) size)) {
            return 0;
        }
    }
    return 1;
}

static int goldberry_md_enter_block(MD_BLOCKTYPE type, void *detail, void *userdata) {
    goldberry_md_stream_t *stream = (goldberry_md_stream_t *) userdata;
    unsigned flags = 0u;
    uint32_t a = 0u;
    uint32_t b = 0u;
    uint32_t c = 0u;

    switch (type) {
        case MD_BLOCK_UL: {
            const MD_BLOCK_UL_DETAIL *ul = (const MD_BLOCK_UL_DETAIL *) detail;
            flags |= ul->is_tight ? GB_MD_FLAG_TIGHT : 0u;
            a = (uint32_t) (unsigned char) ul->mark;
            break;
        }
        case MD_BLOCK_OL: {
            const MD_BLOCK_OL_DETAIL *ol = (const MD_BLOCK_OL_DETAIL *) detail;
            flags |= ol->is_tight ? GB_MD_FLAG_TIGHT : 0u;
            a = (uint32_t) ol->start;
            b = (uint32_t) (unsigned char) ol->mark_delimiter;
            break;
        }
        case MD_BLOCK_LI: {
            const MD_BLOCK_LI_DETAIL *li = (const MD_BLOCK_LI_DETAIL *) detail;
            flags |= li->is_task ? GB_MD_FLAG_TASK : 0u;
            a = (uint32_t) (unsigned char) li->task_mark;
            b = (uint32_t) li->task_mark_offset;
            break;
        }
        case MD_BLOCK_H: {
            const MD_BLOCK_H_DETAIL *h = (const MD_BLOCK_H_DETAIL *) detail;
            a = (uint32_t) h->level;
            break;
        }
        case MD_BLOCK_CODE: {
            const MD_BLOCK_CODE_DETAIL *code = (const MD_BLOCK_CODE_DETAIL *) detail;
            if (!goldberry_md_attribute(stream, GB_MD_ROLE_PRIMARY, &code->info)
                    || !goldberry_md_attribute(stream, GB_MD_ROLE_SECONDARY, &code->lang)) {
                return 1;
            }
            c = (uint32_t) (unsigned char) code->fence_char;
            break;
        }
        case MD_BLOCK_TABLE: {
            const MD_BLOCK_TABLE_DETAIL *table = (const MD_BLOCK_TABLE_DETAIL *) detail;
            a = (uint32_t) table->col_count;
            b = (uint32_t) table->head_row_count;
            c = (uint32_t) table->body_row_count;
            break;
        }
        case MD_BLOCK_TH:
        case MD_BLOCK_TD: {
            const MD_BLOCK_TD_DETAIL *cell = (const MD_BLOCK_TD_DETAIL *) detail;
            a = (uint32_t) cell->align;
            break;
        }
        default:
            break;
    }
    return goldberry_md_record(stream, GB_MD_ENTER_BLOCK, (unsigned) type, GB_MD_ROLE_NONE, flags,
                              a, b, c, NULL, 0u) ? 0 : 1;
}

static int goldberry_md_leave_block(MD_BLOCKTYPE type, void *detail, void *userdata) {
    (void) detail;
    return goldberry_md_record((goldberry_md_stream_t *) userdata, GB_MD_LEAVE_BLOCK, (unsigned) type,
                              GB_MD_ROLE_NONE, 0u, 0u, 0u, 0u, NULL, 0u) ? 0 : 1;
}

static int goldberry_md_enter_span(MD_SPANTYPE type, void *detail, void *userdata) {
    goldberry_md_stream_t *stream = (goldberry_md_stream_t *) userdata;
    unsigned flags = 0u;

    switch (type) {
        case MD_SPAN_A: {
            const MD_SPAN_A_DETAIL *link = (const MD_SPAN_A_DETAIL *) detail;
            if (!goldberry_md_attribute(stream, GB_MD_ROLE_PRIMARY, &link->href)
                    || !goldberry_md_attribute(stream, GB_MD_ROLE_SECONDARY, &link->title)) {
                return 1;
            }
            flags |= link->is_autolink ? GB_MD_FLAG_AUTOLINK : 0u;
            break;
        }
        case MD_SPAN_IMG: {
            const MD_SPAN_IMG_DETAIL *image = (const MD_SPAN_IMG_DETAIL *) detail;
            if (!goldberry_md_attribute(stream, GB_MD_ROLE_PRIMARY, &image->src)
                    || !goldberry_md_attribute(stream, GB_MD_ROLE_SECONDARY, &image->title)) {
                return 1;
            }
            break;
        }
        case MD_SPAN_WIKILINK: {
            const MD_SPAN_WIKILINK_DETAIL *wiki = (const MD_SPAN_WIKILINK_DETAIL *) detail;
            if (!goldberry_md_attribute(stream, GB_MD_ROLE_PRIMARY, &wiki->target)) {
                return 1;
            }
            break;
        }
        default:
            break;
    }
    return goldberry_md_record(stream, GB_MD_ENTER_SPAN, (unsigned) type, GB_MD_ROLE_NONE, flags,
                              0u, 0u, 0u, NULL, 0u) ? 0 : 1;
}

static int goldberry_md_leave_span(MD_SPANTYPE type, void *detail, void *userdata) {
    (void) detail;
    return goldberry_md_record((goldberry_md_stream_t *) userdata, GB_MD_LEAVE_SPAN, (unsigned) type,
                              GB_MD_ROLE_NONE, 0u, 0u, 0u, 0u, NULL, 0u) ? 0 : 1;
}

static int goldberry_md_text(MD_TEXTTYPE type, const MD_CHAR *text, MD_SIZE size, void *userdata) {
    return goldberry_md_record((goldberry_md_stream_t *) userdata, GB_MD_TEXT, (unsigned) type,
                              GB_MD_ROLE_NONE, 0u, 0u, 0u, 0u, text, (uint32_t) size) ? 0 : 1;
}

/*
 * Parses `text` and returns an opaque stream of encoded events, or NULL.
 *
 * NULL means the document could not be turned into a stream -- md4c reported a
 * runtime error, or this side ran out of memory part way through. It does not
 * mean "not Markdown": there is no such document, and an empty input is a stream
 * holding an empty MD_BLOCK_DOC.
 *
 * The caller owns the result and must hand it to goldberry_md_free().
 */
GOLDBERRY_EXPORT void *goldberry_md_parse(const char *text, uint32_t size, uint32_t flags) {
    MD_PARSER parser;
    goldberry_md_stream_t *stream;
    int result;

    if (text == NULL && size > 0u) {
        return NULL;
    }
    stream = (goldberry_md_stream_t *) calloc(1u, sizeof(goldberry_md_stream_t));
    if (stream == NULL) {
        return NULL;
    }

    memset(&parser, 0, sizeof(parser));
    parser.abi_version = 0u;
    parser.flags = flags;
    parser.enter_block = goldberry_md_enter_block;
    parser.leave_block = goldberry_md_leave_block;
    parser.enter_span = goldberry_md_enter_span;
    parser.leave_span = goldberry_md_leave_span;
    parser.text = goldberry_md_text;
    parser.debug_log = NULL;
    parser.syntax = NULL;

    result = md_parse(text == NULL ? "" : text, (MD_SIZE) size, &parser, stream);
    if (result != 0 || stream->failed) {
        free(stream->bytes);
        free(stream);
        return NULL;
    }
    return stream;
}

/*
 * The encoded events. A zero-length segment on the Java side, resized against
 * goldberry_md_size() -- a bare pointer carries no extent, the same contract
 * goldberry_layout_table() has.
 */
GOLDBERRY_EXPORT const void *goldberry_md_data(void *handle) {
    return handle == NULL ? NULL : ((goldberry_md_stream_t *) handle)->bytes;
}

GOLDBERRY_EXPORT uint32_t goldberry_md_size(void *handle) {
    return handle == NULL ? 0u : (uint32_t) ((goldberry_md_stream_t *) handle)->length;
}

GOLDBERRY_EXPORT void goldberry_md_free(void *handle) {
    goldberry_md_stream_t *stream = (goldberry_md_stream_t *) handle;

    if (stream == NULL) {
        return;
    }
    free(stream->bytes);
    free(stream);
}

/*
 * Resolves an HTML5 named entity -- `amp`, `nbsp`, `CounterClockwiseContourIntegral`
 * -- into its one or two codepoints, writing them into `out[0]` and `out[1]`.
 *
 * `name` is the reference including its `&` and `;`, which is what md4c's own
 * table is keyed on and what a MD_TEXT_ENTITY run carries.
 *
 * This is the one thing beyond the parse that has to come from C: the table is
 * 2125 names, it ships inside md4c, and a second copy in Java would be a copy
 * that drifts -- ADR-0010's argument about struct offsets, applied to data.
 *
 * @return 1 when the name is an entity, 0 when it is not
 */
GOLDBERRY_EXPORT int goldberry_md_entity(const char *name, uint32_t size, uint32_t *out) {
    const ENTITY *entity;

    if (name == NULL || out == NULL || size == 0u) {
        return 0;
    }
    entity = entity_lookup(name, (size_t) size);
    if (entity == NULL) {
        return 0;
    }
    out[0] = (uint32_t) entity->codepoints[0];
    out[1] = (uint32_t) entity->codepoints[1];
    return 1;
}
