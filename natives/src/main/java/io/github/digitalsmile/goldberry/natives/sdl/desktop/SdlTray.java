package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.Upcalls;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlSurfaceCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlTrayCalls;
import io.github.digitalsmile.goldberry.natives.sdl.window.SdlPixelFormat;

/// An icon in the desktop's notification area, and the menu the shell draws for
/// it.
///
/// **The one widget in the catalog Goldberry does not paint.** Everything else
/// goes through the cascade, Blend2D and the toolkit's own hit testing; a tray
/// menu is a GTK menu, an `NSMenu` or a Win32 popup, themed by the desktop and
/// operated by it. So this takes a *description* — a list of [SdlTrayItem] — and
/// nothing here has a size, a colour or an event.
///
/// ## Absence is the normal answer
///
/// [#open] returns empty when there is no tray to put an icon in, and that
/// covers more cases than a missing feature usually does: a Linux session with
/// no AppIndicator library, a desktop that removed the notification area, a
/// container with no shell at all, and SDL's own `dummy` backend. Unlike
/// `SDL_CreatePopupWindow`, whose failure is read to tell "this driver has no
/// popups" from "you passed nonsense", **no error string is inspected here** —
/// the Linux path fails with `Could not load AppIndicator libraries`, which is
/// an absence wearing the words of a failure, and `core-widgets.md` §9 asks for
/// absence to be reported rather than thrown either way.
///
/// ## Lifetime
///
/// Two arenas, for two lifetimes. The **labels and the icon surface** live only
/// for the call that hands them over: SDL copies a label into its own storage
/// and converts the icon immediately, which is checked against the pinned source
/// rather than assumed. The **upcall stubs** live as long as the tray, because
/// the shell may call one at any time until [#close] — and closing destroys the
/// tray *before* the arena, in that order, for the reason
/// [io.github.digitalsmile.goldberry.natives.sdl.SdlEventWatch] gives: native
/// code must stop being able to call a stub before the memory holding it goes
/// away.
///
/// Confined to the thread that created it.
public final class SdlTray implements AutoCloseable {

    private static final Logger LOG = Logs.of(SdlTray.class);

    private static final Linker LINKER = Linker.nativeLinker();

    /// ```c
    /// typedef void (SDLCALL *SDL_TrayCallback)(void *userdata, SDL_TrayEntry *entry);
    /// ```
    private static final FunctionDescriptor DESCRIPTOR =
            Upcalls.describe(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle INVOKE = invokeHandle();

    /// A row that has a handler, paired with the pointer SDL knows it by.
    private record Row(MemorySegment entry, SdlTrayItem.Chosen onChosen, boolean checkbox) {}

    private final SdlTrayCalls trayCalls;
    private final SdlSurfaceCalls surfaceCalls;
    private final Arena stubs = Arena.ofShared();
    private final List<Row> rows = new ArrayList<>();
    private final Thread owner = Thread.currentThread();

    private final MemorySegment tray;

    private boolean closed;

    /// Puts an icon in the tray, with a menu under it.
    ///
    /// @param icon    the icon's pixels, or null for the platform's default
    /// @param tooltip the hover text, or null for none
    /// @param items   the menu's rows; an empty list still creates the menu,
    ///                because a tray whose menu appears on the second click is
    ///                worse than an empty one
    /// @return the tray, or empty if this desktop has none
    public static Optional<SdlTray> open(SdlTrayIcon icon, String tooltip, List<SdlTrayItem> items) {
        return open(NativeLibrary.get().lookup(), icon, tooltip, items);
    }

    static Optional<SdlTray> open(SymbolLookup lookup, SdlTrayIcon icon, String tooltip, List<SdlTrayItem> items) {

        Objects.requireNonNull(items, "items");
        var trayCalls = SdlTrayCalls.bind(lookup);
        var surfaceCalls = SdlSurfaceCalls.bind(lookup);

        MemorySegment handle;
        try (var scratch = Arena.ofConfined()) {
            var surface = surfaceOf(surfaceCalls, scratch, icon);
            try {
                handle = trayCalls
                        .createTray()
                        .call(surface, tooltip == null ? MemorySegment.NULL : scratch.allocateFrom(tooltip));
            } finally {
                if (!MemorySegment.NULL.equals(surface)) {
                    surfaceCalls.destroySurface().call(surface);
                }
            }
        }
        if (MemorySegment.NULL.equals(handle)) {
            // Every reason is an absence -- see the class note. Logged at debug
            // with SDL's own words, because "why is there no tray icon" is a
            // question somebody will ask of a session, not of the code.
            LOG.debug("this desktop has no system tray: {}", Sdl.get().lastError());
            return Optional.empty();
        }
        return Optional.of(new SdlTray(trayCalls, surfaceCalls, handle, items));
    }

    private SdlTray(SdlTrayCalls trayCalls, SdlSurfaceCalls surfaceCalls, MemorySegment tray, List<SdlTrayItem> items) {

        this.trayCalls = trayCalls;
        this.surfaceCalls = surfaceCalls;
        this.tray = tray;
        try {
            var menu = trayCalls.createTrayMenu().call(tray);
            if (MemorySegment.NULL.equals(menu)) {
                throw new SdlException("SDL_CreateTrayMenu", Sdl.get().lastError());
            }
            try (var scratch = Arena.ofConfined()) {
                fill(menu, items, scratch);
            }
        } catch (RuntimeException | Error e) {
            // The tray is up and unusable at this point, so it comes down with
            // the stubs rather than being left in the notification area.
            trayCalls.destroyTray().call(tray);
            stubs.close();
            throw e;
        }
    }

    /// Adds every row of `items` to `menu`, recursing into submenus.
    private void fill(MemorySegment menu, List<SdlTrayItem> items, Arena scratch) {
        for (var item : items) {
            var label =
                    item.kind() == SdlTrayItem.Kind.SEPARATOR ? MemorySegment.NULL : scratch.allocateFrom(item.label());
            // -1 appends, which is what a list read front to back wants.
            var entry = trayCalls.insertTrayEntryAt().call(menu, -1, label, SdlTrayEntryFlag.mask(item.flags()));
            if (MemorySegment.NULL.equals(entry)) {
                throw new SdlException("SDL_InsertTrayEntryAt", Sdl.get().lastError());
            }
            if (item.kind() == SdlTrayItem.Kind.SUBMENU) {
                var submenu = trayCalls.createTraySubmenu().call(entry);
                if (MemorySegment.NULL.equals(submenu)) {
                    throw new SdlException("SDL_CreateTraySubmenu", Sdl.get().lastError());
                }
                fill(submenu, item.children(), scratch);
                continue;
            }
            if (item.onChosen() == null) {
                continue;
            }
            var index = rows.size();
            rows.add(new Row(entry, item.onChosen(), item.kind() == SdlTrayItem.Kind.CHECKBOX));
            // One stub per row, with the row's index bound into it, rather than
            // one stub reading an index out of `userdata`. The alternative would
            // mean fabricating a pointer that is never a pointer, and SDL would
            // hand it straight back -- correct, and a lie in the type.
            var target = MethodHandles.insertArguments(INVOKE.bindTo(this), 0, index);
            trayCalls.setTrayEntryCallback().call(entry, upcallStub(target, stubs), MemorySegment.NULL);
        }
    }

    /// Replaces the icon. What a theme switch calls: the tray sits on the
    /// desktop's background, not on the toolkit's, so light and dark are the
    /// shell's question and not the cascade's.
    public void icon(SdlTrayIcon icon) {
        requireOwner();
        requireOpen();
        try (var scratch = Arena.ofConfined()) {
            var surface = surfaceOf(surfaceCalls, scratch, icon);
            try {
                trayCalls.setTrayIcon().call(tray, surface);
            } finally {
                if (!MemorySegment.NULL.equals(surface)) {
                    surfaceCalls.destroySurface().call(surface);
                }
            }
        }
    }

    /// Replaces the hover text. Silently ignored by platforms that have none,
    /// which is SDL's behaviour and not a check that could be added here.
    public void tooltip(String tooltip) {
        requireOwner();
        requireOpen();
        try (var scratch = Arena.ofConfined()) {
            trayCalls.setTrayTooltip().call(tray, tooltip == null ? MemorySegment.NULL : scratch.allocateFrom(tooltip));
        }
    }

    /// Whether this tray has been taken down.
    public boolean isClosed() {
        return closed;
    }

    /// Removes the icon and everything under it. Idempotent.
    ///
    /// The order is the whole content of this method: the tray goes first, so
    /// nothing can call a stub after the arena holding it is closed.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        try {
            trayCalls.destroyTray().call(tray);
        } finally {
            rows.clear();
            stubs.close();
        }
    }

    /// The upcall target. Called by native code, so it must not throw.
    private void invoke(int index, MemorySegment userdata, MemorySegment entry) {
        try {
            if (closed || index >= rows.size()) {
                // A click that arrived while the tray was coming down. The shell
                // owns the timing here, so this is a race to absorb rather than
                // a state to forbid.
                return;
            }
            var row = rows.get(index);
            // SDL applies the toggle before it calls back, so the state is read
            // rather than computed -- and a non-checkbox is never asked, because
            // SDL_GetTrayEntryChecked on one is undefined.
            var checked = row.checkbox() && trayCalls.getTrayEntryChecked().call(row.entry());
            if (Thread.currentThread() != owner) {
                // Not refused, because refusing would lose the click and every
                // platform dispatches this from inside the pump the UI thread is
                // in. Said out loud because if it ever is not, this line is the
                // only evidence there would be.
                LOG.warn(
                        "a tray entry was chosen on {} rather than on {}",
                        Thread.currentThread().getName(),
                        owner.getName());
            }
            row.onChosen().chosen(checked);
        } catch (Throwable t) {
            // An exception escaping an upcall takes the process with it, and the
            // process here is somebody's application being closed from its own
            // tray menu.
            LOG.warn("a tray entry's handler failed", t);
        }
    }

    /// Wraps an icon's pixels as an `SDL_Surface`, or NULL when there is no icon.
    private static MemorySegment surfaceOf(SdlSurfaceCalls calls, Arena scratch, SdlTrayIcon icon) {

        if (icon == null) {
            return MemorySegment.NULL;
        }
        var pixels = MemorySegment.ofBuffer(icon.pixels());
        var surface = calls.createSurfaceFrom()
                .call(icon.width(), icon.height(), SdlPixelFormat.ARGB8888.value(), pixels, icon.stride());
        if (MemorySegment.NULL.equals(surface)) {
            throw new SdlException("SDL_CreateSurfaceFrom", Sdl.get().lastError());
        }
        return surface;
    }

    // Restricted: the same obligation SdlEventWatch takes on, with the same
    // descriptor shape -- nothing is passed or returned by value.
    @SuppressWarnings("restricted")
    private static MemorySegment upcallStub(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, DESCRIPTOR, arena);
    }

    private static MethodHandle invokeHandle() {
        try {
            return MethodHandles.lookup()
                    .findVirtual(
                            SdlTray.class,
                            "invoke",
                            MethodType.methodType(void.class, int.class, MemorySegment.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("a tray belongs to the thread that created it, and this is not it");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this tray has been closed");
        }
    }

    @Override
    public String toString() {
        return "SdlTray[" + rows.size() + " handlers" + (closed ? ", closed" : "") + "]";
    }
}
