package io.github.digitalsmile.goldberry.natives.sdl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.Upcalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlDialogCalls;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlDialogKind;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlFileDialogCallback;
import io.github.digitalsmile.goldberry.natives.sdl.dialog.SdlFileFilter;

/// The platform's own open, save and folder dialogs.
///
/// **Here rather than in `sdl.desktop` beside the clipboard and the tray**, and
/// the reason is one parameter: a file dialog is modal to a window, and
/// `SDL_Window*` is [SdlWindowHandle]'s package-private secret. Moving the
/// pointer out of this package to reach a wrapper in another one would undo
/// exactly what that class exists to do (`docs/ARCHITECTURE.md` §3.1).
///
/// ## Asynchronous, and the memory has to outlive the call
///
/// `SDL_ShowOpenFileDialog` returns immediately and the answer arrives in a
/// callback — on Linux after a round trip through an XDG portal over DBus, which
/// can take as long as the user takes. SDL's header requires the filter array to
/// stay valid until then, so every request gets an [Arena] of its own, registered
/// under an id that travels as the `userdata` pointer. One dialog is not a
/// lifetime: several can be up at once, which is why this is a map and not a
/// field.
///
/// **The arena is automatic, and that is not laziness.** A shared arena would
/// have to be closed, and there is one moment when the answer can arrive that
/// makes closing impossible: SDL refuses **synchronously**, from inside the very
/// downcall that handed the memory over, when the desktop has no dialog driver —
/// no XDG portal and no `zenity`, which is an ordinary container. The linker
/// holds that arena's session for the duration of the call, so `close()` throws
/// `IllegalStateException` there, and an exception thrown inside an upcall takes
/// the process down. An automatic arena has no close: the memory lives exactly as
/// long as the request that owns it is reachable, which is precisely the contract
/// SDL's header states. The request is removed from the map when the callback
/// arrives, so nothing accumulates and [#pendingRequests()] still reports what is
/// outstanding.
///
/// ## The callback is not on the UI thread
///
/// SDL says so, and on Linux it is the DBus thread — except when it is this one,
/// because a refusal comes back before the call returns. Nothing here hops
/// threads: that is a decision about an event loop and belongs to whoever owns
/// one ([SdlFileDialogCallback]). What this class guarantees is that a request is
/// answered exactly once and that **nothing is thrown back into C**, because an
/// exception crossing that boundary takes the process down.
///
/// Show calls themselves must be made on the thread SDL's video subsystem runs
/// on, like every other window call.
public final class SdlFileDialogs {

    private static final Logger LOG = Logs.of(SdlFileDialogs.class);

    private static final Linker LINKER = Linker.nativeLinker();

    /// ```c
    /// typedef void (SDLCALL *SDL_DialogFileCallback)(void *userdata, const char *const *filelist, int filter);
    /// ```
    private static final FunctionDescriptor DESCRIPTOR =
            Upcalls.describe(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    /// `SDL_DialogFileFilter` is two pointers, and an array of them is what SDL
    /// reads. No `MemoryLayout` is needed for something this shape: the arena
    /// allocates `2 * count` addresses and the two halves of each pair go in at
    /// `2i` and `2i + 1`.
    private static final int FILTER_FIELDS = 2;

    /// One dialog that is up, and the memory SDL is still reading.
    ///
    /// The arena is held rather than used: it is what keeps the filter array and
    /// the starting path reachable, and therefore alive, until this record is
    /// dropped from the map.
    private record Pending(Arena arena, SdlFileDialogCallback callback) {}

    private final SdlDialogCalls dialogCalls;
    private final Arena stubs = Arena.ofShared();
    private final MemorySegment callbackStub;

    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    private static final class Holder {
        private static final SdlFileDialogs INSTANCE =
                new SdlFileDialogs(NativeLibrary.get().lookup());
    }

    /// The process's file dialogs.
    public static SdlFileDialogs get() {
        return Holder.INSTANCE;
    }

    SdlFileDialogs(SymbolLookup lookup) {
        this.dialogCalls = SdlDialogCalls.bind(lookup);
        this.callbackStub = upcallStub();
    }

    /// Puts a dialog up and returns at once.
    ///
    /// @param kind            which of SDL's three functions to call
    /// @param owner           the window to be modal for, or **null** for none —
    ///                        nullable rather than an `Optional` because this
    ///                        module carries no annotations and the parameter is
    ///                        one hop from a C pointer that has the same two states
    /// @param filters         the type dropdown; ignored for
    ///                        [SdlDialogKind#OPEN_FOLDER], which has nothing to filter
    /// @param defaultLocation where to start, or null for the platform's own idea
    /// @param allowMany       whether more than one entry may be chosen; ignored
    ///                        for [SdlDialogKind#SAVE_FILE], which produces one path
    /// @param callback        told once, possibly on another thread
    public void show(
            SdlDialogKind kind,
            SdlWindowHandle owner,
            List<SdlFileFilter> filters,
            String defaultLocation,
            boolean allowMany,
            SdlFileDialogCallback callback) {

        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(filters, "filters");
        Objects.requireNonNull(callback, "callback");

        var window = owner == null ? MemorySegment.NULL : owner.pointer();
        var id = nextId.getAndIncrement();
        var arena = Arena.ofAuto();
        // Registered before the call, not after: SDL may answer from another
        // thread before this one returns, and a callback that cannot find its
        // own request would drop the user's choice.
        pending.put(id, new Pending(arena, callback));
        try {
            var userdata = MemorySegment.ofAddress(id);
            var location = defaultLocation == null ? MemorySegment.NULL : arena.allocateFrom(defaultLocation);
            var used = kind.takesFilters() ? filters : List.<SdlFileFilter>of();
            var filterArray = allocateFilters(arena, used);

            switch (kind) {
                case OPEN_FILE ->
                    dialogCalls
                            .showOpenFileDialog()
                            .call(callbackStub, userdata, window, filterArray, used.size(), location, allowMany);
                case SAVE_FILE ->
                    dialogCalls
                            .showSaveFileDialog()
                            .call(callbackStub, userdata, window, filterArray, used.size(), location);
                case OPEN_FOLDER ->
                    dialogCalls.showOpenFolderDialog().call(callbackStub, userdata, window, location, allowMany);
            }
        } catch (RuntimeException | Error e) {
            pending.remove(id);
            throw e;
        }
    }

    /// How many dialogs are still up and still holding memory — for the test
    /// that an answered dialog lets go of its filters rather than leaking a
    /// request per export.
    public int pendingRequests() {
        return pending.size();
    }

    private static MemorySegment allocateFilters(Arena arena, List<SdlFileFilter> filters) {
        if (filters.isEmpty()) {
            return MemorySegment.NULL;
        }
        var array = arena.allocate(ValueLayout.ADDRESS, (long) filters.size() * FILTER_FIELDS);
        for (var i = 0; i < filters.size(); i++) {
            var filter = filters.get(i);
            array.setAtIndex(ValueLayout.ADDRESS, (long) i * FILTER_FIELDS, arena.allocateFrom(filter.name()));
            array.setAtIndex(ValueLayout.ADDRESS, (long) i * FILTER_FIELDS + 1, arena.allocateFrom(filter.pattern()));
        }
        return array;
    }

    // --- the upcall ----------------------------------------------------------

    /// `void (void* userdata, const char* const* filelist, int filter)`
    ///
    /// **Nothing may be thrown out of here.** An exception crossing back into C
    /// takes the process down, so every failure is logged and swallowed — the
    /// application has already lost the dialog's answer and must not also lose
    /// the process.
    private void answer(MemorySegment userdata, MemorySegment filelist, int filterIndex) {
        try {
            var request = pending.remove(userdata.address());
            if (request == null) {
                LOG.warn("a file dialog answered twice, or after its request was dropped; the answer was lost");
                return;
            }
            var callback = request.callback();
            if (MemorySegment.NULL.equals(filelist)) {
                // NULL is SDL's error case, and the error string is only
                // trustworthy before anything else on this thread calls SDL.
                callback.failed(Sdl.get().lastError());
                return;
            }
            var paths = readFileList(filelist);
            if (paths.isEmpty()) {
                callback.cancelled();
            } else {
                callback.chosen(paths, filterIndex);
            }
        } catch (RuntimeException | Error e) {
            LOG.warn("a file dialog's answer could not be delivered", e);
        }
    }

    /// Copies a NULL-terminated `char**` into Java strings.
    ///
    /// SDL frees the list when this callback returns, so nothing may be kept.
    // Restricted: SDL hands over a bare pointer to an array whose length is
    // marked by a NULL rather than counted, which is what reinterpret is for.
    @SuppressWarnings("restricted")
    private static List<String> readFileList(MemorySegment filelist) {
        var paths = new ArrayList<String>();
        var array = filelist.reinterpret(Long.MAX_VALUE);
        for (var i = 0L; ; i++) {
            var entry = array.getAtIndex(ValueLayout.ADDRESS, i);
            if (MemorySegment.NULL.equals(entry)) {
                return List.copyOf(paths);
            }
            paths.add(entry.reinterpret(Long.MAX_VALUE).getString(0));
        }
    }

    // Restricted: the same obligation SdlTray, SdlClipboard and SdlEventWatch
    // take on. The descriptor passes and returns nothing by value.
    @SuppressWarnings("restricted")
    private MemorySegment upcallStub() {
        try {
            var type = MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, int.class);
            var handle = MethodHandles.lookup().findVirtual(SdlFileDialogs.class, "answer", type);
            return LINKER.upcallStub(handle.bindTo(this), DESCRIPTOR, stubs);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
