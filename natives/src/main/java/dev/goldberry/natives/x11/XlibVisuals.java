package dev.goldberry.natives.x11;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Downcalls;

/// The one question this toolkit asks Xlib: which visual on a screen has an
/// alpha channel.
///
/// ## Why it has to be asked
///
/// SDL's X11 driver honours `SDL_WINDOW_TRANSPARENT` only for an OpenGL
/// window, where the GL visual it looks up can be one with alpha. A window
/// presented through its window surface, which every popup is, gets the
/// display's default visual instead. That visual is 24-bit on every X server in
/// common use, so the window has no alpha channel. Its transparent pixels then
/// reach the compositor as opaque black, and a menu's rounded corners are
/// black squares.
///
/// SDL's own way out is `SDL_VIDEO_X11_WINDOW_VISUALID`, a hint that names the
/// visual the next window is created with. Naming one needs its id, and this
/// class finds it: the screen's 32-bit `TrueColor` visual, which is the ARGB
/// visual a compositing window manager blends.
///
/// ## Loaded the way GLib is, and for the same reason
///
/// libX11 is the system's, not the superbuild's, so it is found by soname
/// rather than on the export list. It is only asked for once SDL has reported an
/// X11 display, so the library is already mapped. The lookup takes another
/// reference to it and loads nothing new.
///
/// UI-thread confined, like the SDL calls it sits between.
public final class XlibVisuals {

    /// The runtime soname, which every distribution's libX11 package installs.
    public static final String SONAME = "libX11.so.6";

    /// `TrueColor` in `X.h`.
    static final int TRUE_COLOR = 4;

    /// The depth with an alpha channel: eight bits each of red, green, blue and
    /// alpha.
    static final int ALPHA_DEPTH = 32;

    /// `XVisualInfo` from `Xutil.h`, as an LP64 compiler lays it out: an
    /// `unsigned long` is eight bytes, and the four bytes after `class` are the
    /// padding that aligns `red_mask`. X11 is only reached on Linux and the BSDs,
    /// all LP64 on the platforms the natives are built for.
    static final StructLayout VISUAL_INFO = MemoryLayout.structLayout(
            ADDRESS.withName("visual"),
            JAVA_LONG.withName("visualid"),
            JAVA_INT.withName("screen"),
            JAVA_INT.withName("depth"),
            JAVA_INT.withName("class"),
            MemoryLayout.paddingLayout(4),
            JAVA_LONG.withName("red_mask"),
            JAVA_LONG.withName("green_mask"),
            JAVA_LONG.withName("blue_mask"),
            JAVA_INT.withName("colormap_size"),
            JAVA_INT.withName("bits_per_rgb"));

    private static final long VISUAL_ID = VISUAL_INFO.byteOffset(MemoryLayout.PathElement.groupElement("visualid"));

    /// `Status XMatchVisualInfo(Display*, int screen, int depth, int class, XVisualInfo*)`.
    ///
    /// A constant, so that initialising this class records the shape for a
    /// native image on a build machine that has no libX11.
    private static final FunctionDescriptor MATCH_VISUAL_INFO =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));

    private static final Logger LOG = Logs.of(XlibVisuals.class);

    private static final class Holder {
        private static final @Nullable MethodHandle MATCH = find();
    }

    private XlibVisuals() {}

    /// The id of `screen`'s 32-bit `TrueColor` visual on `display`, or empty
    /// where the server has none or there is no libX11 to ask.
    ///
    /// @param display an Xlib `Display*`, as SDL reports it for a window
    /// @param screen  the screen number the window is on
    public static OptionalLong alphaVisual(MemorySegment display, int screen) {
        Objects.requireNonNull(display, "display");
        var match = Holder.MATCH;
        if (match == null || MemorySegment.NULL.equals(display)) {
            return OptionalLong.empty();
        }
        try (var arena = Arena.ofConfined()) {
            var info = arena.allocate(VISUAL_INFO);
            int found;
            try {
                found = (int) match.invokeExact(display, screen, ALPHA_DEPTH, TRUE_COLOR, info);
            } catch (Throwable t) {
                throw Downcalls.failure("XMatchVisualInfo", t);
            }
            return found == 0 ? OptionalLong.empty() : OptionalLong.of(info.get(JAVA_LONG, VISUAL_ID));
        }
    }

    /// A visual id as `SDL_VIDEO_X11_WINDOW_VISUALID` reads it: SDL parses the
    /// hint with `strtol(…, 0)`, so a `0x` prefix is read as hexadecimal, which
    /// is how `xdpyinfo` and `xwininfo` print visual ids.
    public static String hintValue(long visualId) {
        if (visualId <= 0) {
            throw new IllegalArgumentException("a visual id is positive, and " + visualId + " is not");
        }
        return "0x" + Long.toHexString(visualId);
    }

    // Restricted for GlibLibrary's reason: opening a system library is what this
    // module exists to do.
    @SuppressWarnings("restricted")
    private static @Nullable MethodHandle find() {
        try {
            var lookup = SymbolLookup.libraryLookup(SONAME, Arena.global());
            var address = lookup.find("XMatchVisualInfo").orElse(null);
            if (address == null) {
                LOG.debug("{} has no XMatchVisualInfo; popups keep the default visual", SONAME);
                return null;
            }
            return Downcalls.link(MATCH_VISUAL_INFO).bindTo(address);
        } catch (IllegalArgumentException e) {
            LOG.debug("no {} ({}); popups keep the default visual", SONAME, e.getMessage());
            return null;
        }
    }
}
