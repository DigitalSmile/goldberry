package dev.goldberry.natives.x11;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Downcalls;

/// Whether a compositing manager is running on an X11 screen: the question that
/// decides whether a popup's transparent pixels are seen through or painted
/// black.
///
/// ## How X11 says it
///
/// A compositing manager owns the selection `_NET_WM_CM_S<screen>` for as long
/// as it runs; the EWMH specification makes that ownership the announcement. So
/// the question is two calls: the atom for the name, and the selection's owner,
/// which is `None` (zero) when nobody composites. A desktop without one shows a
/// window's alpha channel as nothing at all, so a popup's rounded corners and a
/// shadow's margin come out opaque black.
///
/// Asked each time rather than kept: a compositing manager can be started and
/// stopped while the application runs, and two round trips to the server per
/// popup is nothing beside opening a window.
///
/// Loaded as [XlibVisuals] is, by soname, once SDL has reported an X11 display.
/// UI-thread confined.
public final class XlibCompositing {

    /// `Atom XInternAtom(Display*, const char* name, Bool only_if_exists)`.
    /// A constant, so that initialising this class records the shape for a native
    /// image on a build machine that has no libX11.
    private static final FunctionDescriptor INTERN_ATOM =
            Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT));

    /// `Window XGetSelectionOwner(Display*, Atom selection)`.
    private static final FunctionDescriptor GET_SELECTION_OWNER =
            Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG));

    private static final Logger LOG = Logs.of(XlibCompositing.class);

    private record Calls(MethodHandle internAtom, MethodHandle selectionOwner) {}

    private static final class Holder {
        private static final @Nullable Calls CALLS = find();
    }

    private XlibCompositing() {}

    /// The selection a compositing manager on `screen` owns.
    public static String selectionName(int screen) {
        if (screen < 0) {
            throw new IllegalArgumentException("a screen number is not negative, and " + screen + " is");
        }
        return "_NET_WM_CM_S" + screen;
    }

    /// Whether a compositing manager owns `screen` on `display`. False where there
    /// is no libX11 to ask, which is the answer that draws nothing it cannot show.
    ///
    /// @param display an Xlib `Display*`, as SDL reports it for a window
    /// @param screen  the screen number the window is on
    public static boolean isCompositing(MemorySegment display, int screen) {
        Objects.requireNonNull(display, "display");
        var calls = Holder.CALLS;
        if (calls == null || MemorySegment.NULL.equals(display)) {
            return false;
        }
        try (var arena = Arena.ofConfined()) {
            var name = arena.allocateFrom(selectionName(screen));
            long atom;
            long owner;
            try {
                // `only_if_exists`: an atom nobody has interned names no selection
                // anybody owns, and creating it would leave a name on the server
                // for nothing.
                atom = (long) calls.internAtom().invokeExact(display, name, 1);
                if (atom == 0) {
                    return false;
                }
                owner = (long) calls.selectionOwner().invokeExact(display, atom);
            } catch (Throwable t) {
                throw Downcalls.failure("XGetSelectionOwner", t);
            }
            return owner != 0;
        }
    }

    // Restricted for XlibVisuals' reason.
    @SuppressWarnings("restricted")
    private static @Nullable Calls find() {
        try {
            var lookup = SymbolLookup.libraryLookup(XlibVisuals.SONAME, Arena.global());
            var intern = lookup.find("XInternAtom").orElse(null);
            var owner = lookup.find("XGetSelectionOwner").orElse(null);
            if (intern == null || owner == null) {
                LOG.debug("{} lacks XInternAtom or XGetSelectionOwner; popups are taken as opaque", XlibVisuals.SONAME);
                return null;
            }
            return new Calls(
                    Downcalls.link(INTERN_ATOM).bindTo(intern),
                    Downcalls.link(GET_SELECTION_OWNER).bindTo(owner));
        } catch (IllegalArgumentException e) {
            LOG.debug("no {} ({}); popups are taken as opaque", XlibVisuals.SONAME, e.getMessage());
            return null;
        }
    }
}
