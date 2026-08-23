package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// SDL's system tray — an icon in the desktop's notification area, and the menu
/// the shell draws for it.
///
/// **Nothing here paints.** The menu these calls build belongs to the platform:
/// it is a GTK menu on Linux, an `NSMenu` on macOS and a Win32 popup on Windows,
/// drawn by the shell in the shell's own theme. That is the whole reason a tray
/// menu is a description handed over rather than a widget tree — Goldberry's
/// cascade, fonts and hit testing reach none of it.
///
/// `SDL_UpdateTrays` is deliberately unbound: SDL calls it from its own event
/// loop, and this toolkit pumps events.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlTrayCalls(
        CreateTray createTray,
        DestroyTray destroyTray,
        SetTrayIcon setTrayIcon,
        SetTrayTooltip setTrayTooltip,
        CreateTrayMenu createTrayMenu,
        CreateTraySubmenu createTraySubmenu,
        InsertTrayEntryAt insertTrayEntryAt,
        SetTrayEntryCallback setTrayEntryCallback,
        GetTrayEntryChecked getTrayEntryChecked) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlTrayCalls bind(SymbolLookup lookup) {
        return new SdlTrayCalls(
                new CreateTray(lookup),
                new DestroyTray(lookup),
                new SetTrayIcon(lookup),
                new SetTrayTooltip(lookup),
                new CreateTrayMenu(lookup),
                new CreateTraySubmenu(lookup),
                new InsertTrayEntryAt(lookup),
                new SetTrayEntryCallback(lookup),
                new GetTrayEntryChecked(lookup));
    }

    /// Puts an icon in the tray.
    ///
    /// Returns NULL when the desktop has no tray Goldberry can reach — SDL's
    /// `dummy` tray backend, a Linux session with no AppIndicator library, a
    /// headless container. **Every one of those is an absence rather than a
    /// failure**, which is why the wrapper does not read the error to tell them
    /// apart the way `SDL_CreatePopupWindow`'s caller does.
    ///
    /// `void* SDL_CreateTray(void*, void*)`
    ///
    /// @param icon    an `SDL_Surface*`, or NULL for the platform's default
    /// @param tooltip the hover text, NUL-terminated UTF-8, or NULL
    /// @return an `SDL_Tray*`, or NULL if this desktop has no tray
    public static final class CreateTray {

        private static final MethodHandle FD_SDL_CreateTray =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateTray(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateTray");
        }

        public MemorySegment call(MemorySegment icon, MemorySegment tooltip) {
            try {
                return (MemorySegment) FD_SDL_CreateTray.invokeExact(address, icon, tooltip);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateTray", t);
            }
        }
    }

    /// Removes the icon and destroys every menu and entry hanging off it.
    ///
    /// An entry is never destroyed on its own, which is what lets the wrapper
    /// hold one upcall stub per entry and free them all at once.
    ///
    /// `void SDL_DestroyTray(void*)`
    ///
    /// @param tray the tray to take down
    public static final class DestroyTray {

        private static final MethodHandle FD_SDL_DestroyTray =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DestroyTray(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyTray");
        }

        public void call(MemorySegment tray) {
            try {
                FD_SDL_DestroyTray.invokeExact(address, tray);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyTray", t);
            }
        }
    }

    /// Replaces the icon — what a theme switch calls, since the tray sits on the
    /// desktop's background and not on the toolkit's.
    ///
    /// `void SDL_SetTrayIcon(void*, void*)`
    ///
    /// @param tray the tray
    /// @param icon an `SDL_Surface*`, or NULL for the platform's default
    public static final class SetTrayIcon {

        private static final MethodHandle FD_SDL_SetTrayIcon =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetTrayIcon(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetTrayIcon");
        }

        public void call(MemorySegment tray, MemorySegment icon) {
            try {
                FD_SDL_SetTrayIcon.invokeExact(address, tray, icon);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetTrayIcon", t);
            }
        }
    }

    /// Replaces the hover text. Not supported on every platform, and SDL says so
    /// by doing nothing.
    ///
    /// `void SDL_SetTrayTooltip(void*, void*)`
    ///
    /// @param tray    the tray
    /// @param tooltip NUL-terminated UTF-8, or NULL for none
    public static final class SetTrayTooltip {

        private static final MethodHandle FD_SDL_SetTrayTooltip =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetTrayTooltip(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetTrayTooltip");
        }

        public void call(MemorySegment tray, MemorySegment tooltip) {
            try {
                FD_SDL_SetTrayTooltip.invokeExact(address, tray, tooltip);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetTrayTooltip", t);
            }
        }
    }

    /// Creates the tray's menu. Called once per tray: SDL refuses a second one,
    /// and the wrapper never asks for it.
    ///
    /// `void* SDL_CreateTrayMenu(void*)`
    ///
    /// @param tray the tray to give a menu
    /// @return an `SDL_TrayMenu*`, or NULL
    public static final class CreateTrayMenu {

        private static final MethodHandle FD_SDL_CreateTrayMenu =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateTrayMenu(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateTrayMenu");
        }

        public MemorySegment call(MemorySegment tray) {
            try {
                return (MemorySegment) FD_SDL_CreateTrayMenu.invokeExact(address, tray);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateTrayMenu", t);
            }
        }
    }

    /// Creates the menu that hangs off an entry created with
    /// `SDL_TRAYENTRY_SUBMENU`.
    ///
    /// `void* SDL_CreateTraySubmenu(void*)`
    ///
    /// @param entry the entry to open a submenu on
    /// @return an `SDL_TrayMenu*`, or NULL
    public static final class CreateTraySubmenu {

        private static final MethodHandle FD_SDL_CreateTraySubmenu =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateTraySubmenu(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateTraySubmenu");
        }

        public MemorySegment call(MemorySegment entry) {
            try {
                return (MemorySegment) FD_SDL_CreateTraySubmenu.invokeExact(address, entry);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateTraySubmenu", t);
            }
        }
    }

    /// Adds an entry to a menu.
    ///
    /// A **NULL label is a separator**, which is SDL's spelling of the thing
    /// `separator` is a widget for on this side of the boundary.
    ///
    /// `void* SDL_InsertTrayEntryAt(void*, int, void*, int)`
    ///
    /// @param menu  the menu to append to
    /// @param pos   the position, or -1 to append
    /// @param label NUL-terminated UTF-8, or NULL for a separator
    /// @param flags an `SDL_TrayEntryFlags` mask with exactly one kind in it
    /// @return an `SDL_TrayEntry*`, or NULL if `pos` was out of bounds
    public static final class InsertTrayEntryAt {

        private static final MethodHandle FD_SDL_InsertTrayEntryAt =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        InsertTrayEntryAt(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_InsertTrayEntryAt");
        }

        public MemorySegment call(MemorySegment menu, int pos, MemorySegment label, int flags) {
            try {
                return (MemorySegment) FD_SDL_InsertTrayEntryAt.invokeExact(
                        address, menu, pos, label, flags);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_InsertTrayEntryAt", t);
            }
        }
    }

    /// Says what to run when an entry is chosen.
    ///
    /// `void SDL_SetTrayEntryCallback(void*, void*, void*)`
    ///
    /// @param entry    the entry
    /// @param callback an `SDL_TrayCallback` upcall stub, or NULL to clear
    /// @param userdata passed back to the callback; the wrapper passes NULL and
    ///        binds the entry's identity into the stub instead
    public static final class SetTrayEntryCallback {

        private static final MethodHandle FD_SDL_SetTrayEntryCallback =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetTrayEntryCallback(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetTrayEntryCallback");
        }

        public void call(MemorySegment entry, MemorySegment callback, MemorySegment userdata) {
            try {
                FD_SDL_SetTrayEntryCallback.invokeExact(address, entry, callback, userdata);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetTrayEntryCallback", t);
            }
        }
    }

    /// Whether a checkbox entry is checked.
    ///
    /// The one read-back the tray needs: SDL toggles a checkbox before it calls
    /// back, so this is the only way to learn what the user chose.
    ///
    /// `_Bool SDL_GetTrayEntryChecked(void*)`
    ///
    /// @param entry the entry, which must be a checkbox
    public static final class GetTrayEntryChecked {

        private static final MethodHandle FD_SDL_GetTrayEntryChecked =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        GetTrayEntryChecked(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetTrayEntryChecked");
        }

        public boolean call(MemorySegment entry) {
            try {
                return (boolean) FD_SDL_GetTrayEntryChecked.invokeExact(address, entry);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetTrayEntryChecked", t);
            }
        }
    }
}
