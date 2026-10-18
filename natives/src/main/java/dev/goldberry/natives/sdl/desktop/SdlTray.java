package dev.goldberry.natives.sdl.desktop;

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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.NativeLibrary;
import dev.goldberry.natives.Upcalls;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.calls.SdlSurfaceCalls;
import dev.goldberry.natives.sdl.calls.SdlTrayCalls;
import dev.goldberry.natives.sdl.window.SdlPixelFormat;

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
/// an absence wearing the words of a failure, and the tray's rule is that an
/// absence is reported rather than thrown either way.
///
/// ## Lifetime
///
/// Two arenas, for two lifetimes. The **labels and the icon surface** live only
/// for the call that hands them over: SDL copies a label into its own storage
/// and converts the icon immediately, which is checked against the pinned source
/// rather than assumed. The **upcall stubs** live as long as the tray, because
/// the shell may call one at any time until [#close] — and closing destroys the
/// tray *before* the arena, in that order, for the reason
/// [dev.goldberry.natives.sdl.SdlEventWatch] gives: native
/// code must stop being able to call a stub before the memory holding it goes
/// away.
///
/// ## Changing the menu
///
/// [#rows] gives the tray a new description while the icon is up. A menu of the
/// same shape (the same kinds of row, in the same places) is changed in place:
/// a label, a tick, whether a row is offered, and which handler a row runs. A
/// menu of another shape replaces the rows under the same icon. Either way the
/// icon stays where it is, so nothing flickers in the notification area.
///
/// **The description is the truth.** A checkbox shows the tick its description
/// says, so a handler that declines a click describes the menu again with the
/// tick as it was. A change asked for from inside a row's handler is applied
/// when the handler returns, never under SDL's own callback.
///
/// Each shape of the menu has its own arena of upcall stubs. One a reshape
/// leaves behind is closed at the next reshape or at [#close], never while a
/// callback from it can still be on the stack.
///
/// Confined to the thread that created it, but for a row's handler, which runs
/// on whatever thread the platform calls back on.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
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

    /// One entry of the menu as it stands: SDL's pointer, the description it
    /// was last given, and its submenu's entries.
    private record Node(MemorySegment entry, SdlTrayItem item, List<Node> children) {}

    private final SdlTrayCalls trayCalls;
    private final SdlSurfaceCalls surfaceCalls;
    private final Thread owner = Thread.currentThread();

    private final MemorySegment tray;
    private final MemorySegment menu;

    /// The stubs of the menu's current shape.
    private Arena stubs = Arena.ofShared();

    /// The stubs a reshape left behind, closed at the next one or at [#close].
    private @Nullable Arena retired;

    /// Which shape of the menu a stub was made for: a stub carries it, so one
    /// left over from a shape that has gone can never run a row of the new one.
    private int generation;

    /// The handlers, by the index bound into each stub. Replaced whole rather
    /// than changed, since the platform may call back on a thread of its own.
    private volatile List<Row> rows = List.of();

    private List<Node> nodes = List.of();

    /// How deep the calls back into a row's handler are, on the owner's thread.
    private int dispatching;

    /// A description given from inside a handler, applied when it returns.
    private @Nullable List<SdlTrayItem> pending;

    private boolean closed;

    /// Puts an icon in the tray, with a menu under it.
    ///
    /// @param icon    the icon's pixels, or null for the platform's default
    /// @param tooltip the hover text, or null for none
    /// @param items   the menu's rows; an empty list still creates the menu,
    ///                because a tray whose menu appears on the second click is
    ///                worse than an empty one
    /// @return the tray, or empty if this desktop has none
    public static Optional<SdlTray> open(
            @Nullable SdlTrayIcon icon, @Nullable String tooltip, List<SdlTrayItem> items) {
        return open(NativeLibrary.get().lookup(), icon, tooltip, items);
    }

    static Optional<SdlTray> open(
            SymbolLookup lookup, @Nullable SdlTrayIcon icon, @Nullable String tooltip, List<SdlTrayItem> items) {

        Objects.requireNonNull(items, "items");
        var trayCalls = SdlTrayCalls.bind(lookup);
        var surfaceCalls = SdlSurfaceCalls.bind(lookup);

        MemorySegment handle;
        try (var scratch = Arena.ofConfined()) {
            var surface = surfaceOf(surfaceCalls, icon);
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
            this.menu = trayCalls.createTrayMenu().call(tray);
            if (MemorySegment.NULL.equals(menu)) {
                throw new SdlException("SDL_CreateTrayMenu", Sdl.get().lastError());
            }
            var handlers = new ArrayList<Row>();
            try (var scratch = Arena.ofConfined()) {
                nodes = fill(menu, items, scratch, handlers);
            }
            rows = List.copyOf(handlers);
        } catch (RuntimeException | Error e) {
            // The tray is up and unusable at this point, so it comes down with
            // the stubs rather than being left in the notification area.
            trayCalls.destroyTray().call(tray);
            stubs.close();
            throw e;
        }
    }

    /// Adds every row of `items` to `menu`, recursing into submenus, and answers
    /// the entries it made. Each row with a handler is appended to `handlers`,
    /// whose index its stub carries.
    private List<Node> fill(MemorySegment menu, List<SdlTrayItem> items, Arena scratch, List<Row> handlers) {
        var made = new ArrayList<Node>(items.size());
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
                made.add(new Node(entry, item, fill(submenu, item.children(), scratch, handlers)));
                continue;
            }
            made.add(new Node(entry, item, List.of()));
            if (item.onChosen() == null) {
                continue;
            }
            var index = handlers.size();
            handlers.add(new Row(entry, item.onChosen(), item.kind() == SdlTrayItem.Kind.CHECKBOX));
            // One stub per row, with the shape and the row's index bound into
            // it, rather than one stub reading an index out of `userdata`. The
            // alternative would mean fabricating a pointer that is never a
            // pointer, and SDL would hand it straight back -- correct, and a lie
            // in the type.
            var target = MethodHandles.insertArguments(INVOKE.bindTo(this), 0, generation, index);
            trayCalls.setTrayEntryCallback().call(entry, upcallStub(target, stubs), MemorySegment.NULL);
        }
        return List.copyOf(made);
    }

    /// Gives the menu a new description, keeping the icon where it is.
    ///
    /// The same shape is changed in place; another shape replaces the rows. Asked
    /// from inside a row's handler, the change waits until the handler returns.
    ///
    /// @param items the menu's rows, as [#open] takes them
    public void rows(List<SdlTrayItem> items) {
        requireOwner();
        requireOpen();
        var described = List.copyOf(Objects.requireNonNull(items, "items"));
        if (dispatching > 0) {
            pending = described;
            return;
        }
        apply(described);
    }

    private void apply(List<SdlTrayItem> items) {
        if (sameShape(nodes, items)) {
            var handlers = new ArrayList<Row>();
            try (var scratch = Arena.ofConfined()) {
                nodes = update(nodes, items, scratch, handlers);
            }
            rows = List.copyOf(handlers);
            return;
        }
        // Another shape: the old rows out, then the new ones in, under the same
        // icon. A removed submenu takes its own rows with it.
        for (var node : nodes) {
            trayCalls.removeTrayEntry().call(node.entry());
        }
        rows = List.of();
        nodes = List.of();
        if (retired != null) {
            retired.close();
        }
        retired = stubs;
        stubs = Arena.ofShared();
        generation++;
        var handlers = new ArrayList<Row>();
        try (var scratch = Arena.ofConfined()) {
            nodes = fill(menu, items, scratch, handlers);
        }
        rows = List.copyOf(handlers);
    }

    /// Whether `items` has the shape `nodes` was made from: the same kinds in the
    /// same places, the same rows with a handler, the same submenus.
    private static boolean sameShape(List<Node> nodes, List<SdlTrayItem> items) {
        if (nodes.size() != items.size()) {
            return false;
        }
        for (var i = 0; i < items.size(); i++) {
            var was = nodes.get(i).item();
            var now = items.get(i);
            if (was.kind() != now.kind()
                    || (was.onChosen() == null) != (now.onChosen() == null)
                    || !sameShape(nodes.get(i).children(), now.children())) {
                return false;
            }
        }
        return true;
    }

    /// Changes what differs between `nodes` and `items`, which have one shape,
    /// and answers the entries as they now stand.
    private List<Node> update(List<Node> nodes, List<SdlTrayItem> items, Arena scratch, List<Row> handlers) {
        var made = new ArrayList<Node>(items.size());
        for (var i = 0; i < items.size(); i++) {
            var node = nodes.get(i);
            var was = node.item();
            var now = items.get(i);
            var entry = node.entry();
            if (now.kind() != SdlTrayItem.Kind.SEPARATOR) {
                if (!Objects.equals(was.label(), now.label())) {
                    trayCalls.setTrayEntryLabel().call(entry, scratch.allocateFrom(now.label()));
                }
                if (was.enabled() != now.enabled()) {
                    trayCalls.setTrayEntryEnabled().call(entry, now.enabled());
                }
            }
            // Against what the platform shows rather than what was described
            // last: a click toggled it there, and the description is the truth.
            if (now.kind() == SdlTrayItem.Kind.CHECKBOX
                    && trayCalls.getTrayEntryChecked().call(entry) != now.checked()) {
                trayCalls.setTrayEntryChecked().call(entry, now.checked());
            }
            var children = update(node.children(), now.children(), scratch, handlers);
            if (now.onChosen() != null && now.kind() != SdlTrayItem.Kind.SUBMENU) {
                handlers.add(new Row(entry, now.onChosen(), now.kind() == SdlTrayItem.Kind.CHECKBOX));
            }
            made.add(new Node(entry, now, children));
        }
        return List.copyOf(made);
    }

    /// Chooses the row at `path` as the desktop's shell would: a checkbox is
    /// toggled, then the row's handler runs.
    ///
    /// For a test on a real desktop, where nothing can click the shell's menu
    /// from outside it. Paths are the labels from the top of the menu down,
    /// separated by `/`, as `"Recent/report.pdf"`.
    ///
    /// @throws IllegalArgumentException if no row is reached by that path
    public void click(String path) {
        requireOwner();
        requireOpen();
        var node = find(nodes, "", Objects.requireNonNull(path, "path"));
        if (node == null) {
            throw new IllegalArgumentException("no tray row at " + path);
        }
        trayCalls.clickTrayEntry().call(node.entry());
    }

    /// Whether the checkbox at `path` shows a tick, as the platform has it.
    ///
    /// @throws IllegalArgumentException if no checkbox is reached by that path
    public boolean isChecked(String path) {
        requireOwner();
        requireOpen();
        var node = find(nodes, "", Objects.requireNonNull(path, "path"));
        if (node == null || node.item().kind() != SdlTrayItem.Kind.CHECKBOX) {
            throw new IllegalArgumentException("no tray checkbox at " + path);
        }
        return trayCalls.getTrayEntryChecked().call(node.entry());
    }

    private static @Nullable Node find(List<Node> nodes, String prefix, String path) {
        for (var node : nodes) {
            var label = node.item().label();
            if (label == null) {
                continue;
            }
            var here = prefix.isEmpty() ? label : prefix + "/" + label;
            if (here.equals(path)) {
                return node;
            }
            var deeper = find(node.children(), here, path);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    /// Replaces the icon. What a theme switch calls: the tray sits on the
    /// desktop's background, not on the toolkit's, so light and dark are the
    /// shell's question and not the cascade's.
    public void icon(@Nullable SdlTrayIcon icon) {
        requireOwner();
        requireOpen();
        var surface = surfaceOf(surfaceCalls, icon);
        try {
            trayCalls.setTrayIcon().call(tray, surface);
        } finally {
            if (!MemorySegment.NULL.equals(surface)) {
                surfaceCalls.destroySurface().call(surface);
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
            rows = List.of();
            nodes = List.of();
            pending = null;
            stubs.close();
            if (retired != null) {
                retired.close();
                retired = null;
            }
        }
    }

    /// The upcall target. Called by native code, so it must not throw.
    ///
    /// On whatever thread the platform calls back on, which is the owner's
    /// everywhere SDL dispatches from inside the pump. Where it is not, the
    /// handler is the caller's to move: `Sdl3Tray` hands it to the UI thread.
    private void invoke(int shape, int index, MemorySegment userdata, MemorySegment entry) {
        var onOwner = Thread.currentThread() == owner;
        try {
            var current = rows;
            if (closed || shape != generation || index >= current.size()) {
                // A click that arrived while the tray, or this shape of its menu,
                // was coming down. The shell owns the timing here, so this is a
                // race to absorb rather than a state to forbid.
                return;
            }
            var row = current.get(index);
            // SDL applies the toggle before it calls back, so the state is read
            // rather than computed -- and a non-checkbox is never asked, because
            // SDL_GetTrayEntryChecked on one is undefined.
            var checked = row.checkbox() && trayCalls.getTrayEntryChecked().call(row.entry());
            if (onOwner) {
                dispatching++;
            }
            try {
                row.onChosen().chosen(checked);
            } finally {
                if (onOwner) {
                    dispatching--;
                }
            }
            // A description given from inside the handler, now that SDL's own
            // callback is the only thing left on the stack above this one.
            var next = pending;
            if (onOwner && dispatching == 0 && next != null && !closed) {
                pending = null;
                apply(next);
            }
        } catch (Throwable t) {
            // An exception escaping an upcall takes the process with it, and the
            // process here is somebody's application being closed from its own
            // tray menu.
            LOG.warn("a tray entry's handler failed", t);
        }
    }

    /// Wraps an icon's pixels as an `SDL_Surface`, or NULL when there is no icon.
    private static MemorySegment surfaceOf(SdlSurfaceCalls calls, SdlTrayIcon icon) {
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
                            MethodType.methodType(
                                    void.class, int.class, int.class, MemorySegment.class, MemorySegment.class));
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
