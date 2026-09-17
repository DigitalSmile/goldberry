package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.Upcalls;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlClipboardCalls;

/// SDL3's clipboard calls — text, and bytes under a MIME type.
///
/// The facility `docs/ARCHITECTURE.md` §4 listed and
/// [io.github.digitalsmile.goldberry.backend.Backend] left out, on the rule that
/// an interface with no consumer gets designed twice (ADR-0019). `text-input`
/// was the first consumer and wanted three calls; a board that pastes a
/// screenshot is the second, and wants the other half (ADR-0286).
///
/// ## A write is an offer, not a copy
///
/// `SDL_SetClipboardText` copies. **`SDL_SetClipboardData` does not**: it takes a
/// list of MIME types this application can produce and a callback that produces
/// one, and calls that callback if and when somebody pastes — which is what the
/// platform protocols do underneath, where an X11 selection owner is asked to
/// serialise on demand.
///
/// So the bytes stay ours, in a shared arena per offer, and SDL calls a second
/// callback when the offer is replaced or cleared. Each offer carries its own id
/// as `userdata`, because a cleanup for the *previous* offer arrives while the
/// next one is being installed and the two must not be confused.
///
/// The upcall stubs live in an arena that is never closed. They are two for the
/// life of the process — freeing a stub while it is running is the one way this
/// arrangement can crash, and there is nothing to gain by trying.
///
/// **Process-global, like [SdlCursors] and for the same reason** — the clipboard
/// belongs to the session and not to a window. There is no per-window variant to
/// have missed.
///
/// ## Why `SDL_free` is bound
///
/// `SDL_GetClipboardText` returns a string **the caller owns**, allocated by
/// SDL's allocator. Handing that pointer to `free(3)` is undefined whenever SDL
/// was built against a different allocator than the process's — which on Windows
/// is the normal case, not the exotic one — so the matching `SDL_free` is on the
/// export list beside it. It is the only allocator call this toolkit binds, and
/// it exists solely to close this one loop.
///
/// The read is therefore always three steps: call, copy into a Java string, free.
/// Nothing here ever hands a native pointer across the module boundary; what
/// crosses is a `String`.
///
/// Confined to the UI thread, like everything else in this package.
public final class SdlClipboard {

    private static final Logger LOG = Logs.of(SdlClipboard.class);

    private static final class Holder {
        private static final SdlClipboard INSTANCE =
                new SdlClipboard(NativeLibrary.get().lookup());
    }

    private static final Linker LINKER = Linker.nativeLinker();

    private static final byte[] EMPTY = new byte[0];

    /// ```c
    /// typedef const void *(SDLCALL *SDL_ClipboardDataCallback)(
    ///         void *userdata, const char *mime_type, size_t *size);
    /// ```
    private static final FunctionDescriptor DATA = Upcalls.describe(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /// ```c
    /// typedef void (SDLCALL *SDL_ClipboardCleanupCallback)(void *userdata);
    /// ```
    private static final FunctionDescriptor CLEANUP = Upcalls.describe(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private final SdlClipboardCalls sdlClipboardCalls;

    /// The two stubs, for the life of the process — see the note on this class.
    private final Arena stubs = Arena.ofShared();

    /// Every offer that is still holding memory, by the id SDL was given as
    /// `userdata`. More than one at a time is the ordinary case for a moment: the
    /// cleanup for what was on the clipboard arrives while its replacement is
    /// being installed.
    private final Map<Long, Offer> offers = new ConcurrentHashMap<>();

    /// Ids are never reused, so a cleanup that arrives late cannot free the
    /// memory of the offer that took its place.
    private final AtomicLong nextOfferId = new AtomicLong(1);

    private final MemorySegment dataStub;

    private final MemorySegment cleanupStub;

    /// The process's clipboard.
    public static SdlClipboard get() {
        return Holder.INSTANCE;
    }

    SdlClipboard(SymbolLookup lookup) {
        this.sdlClipboardCalls = SdlClipboardCalls.bind(lookup);
        this.dataStub = upcallStub(
                "provide",
                DATA,
                MethodType.methodType(
                        MemorySegment.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
        this.cleanupStub = upcallStub("forget", CLEANUP, MethodType.methodType(void.class, MemorySegment.class));
    }

    /// Whether the clipboard holds any non-empty text.
    ///
    /// Worth asking before [#text()], because on X11 and Wayland the read is a
    /// **round trip to the owning client** and this one is answered from what the
    /// compositor already told us.
    public boolean hasText() {
        return sdlClipboardCalls.hasClipboardText().call();
    }

    /// The clipboard's text, or `""` when it holds none.
    ///
    /// Empty rather than null, because "the clipboard is empty" and "the
    /// clipboard holds an empty string" are the same paste — and SDL itself
    /// returns an empty string rather than NULL on failure, so there is no third
    /// state to report.
    public String text() {
        var pointer = sdlClipboardCalls.getClipboardText().call();
        if (MemorySegment.NULL.equals(pointer)) {
            return "";
        }
        try {
            return readCString(pointer);
        } finally {
            release(pointer);
        }
    }

    /// Puts `text` on the clipboard, replacing whatever was there.
    ///
    /// A refusal is **logged and dropped** rather than thrown. A copy that the
    /// compositor declined is a copy that did not happen, and taking the window
    /// down over it would be worse than the empty paste that follows — the same
    /// argument [SdlCursors] makes for a missing cursor shape.
    ///
    /// @return whether SDL accepted it
    public boolean text(String text) {
        try (var arena = Arena.ofConfined()) {
            var accepted = sdlClipboardCalls.setClipboardText().call(arena.allocateFrom(text == null ? "" : text));
            if (!accepted) {
                LOG.debug("SDL_SetClipboardText() refused: {}", Sdl.get().lastError());
            }
            return accepted;
        }
    }

    // --- bytes under a MIME type ---------------------------------------------

    /// Whether the clipboard can produce `mime`.
    ///
    /// The cheap question, like [#hasText()]: answered from what the compositor
    /// has already advertised rather than by asking the owner to serialise.
    public boolean has(String mime) {
        Objects.requireNonNull(mime, "mime");
        try (var arena = Arena.ofConfined()) {
            return sdlClipboardCalls.hasClipboardData().call(arena.allocateFrom(mime));
        }
    }

    /// The clipboard's bytes for `mime`, or an empty array when it has none.
    ///
    /// Empty rather than null for [#text()]'s reason: "nothing to paste" and "what
    /// was copied was empty" are the same paste.
    ///
    /// A **round trip to the owning application** on X11 and Wayland — see the
    /// note on this class — and the bytes come back in SDL's allocator, so this
    /// copies and frees before returning like every other read here.
    public byte[] read(String mime) {
        Objects.requireNonNull(mime, "mime");
        try (var arena = Arena.ofConfined()) {
            var size = arena.allocate(ValueLayout.JAVA_LONG);
            var pointer = sdlClipboardCalls.getClipboardData().call(arena.allocateFrom(mime), size);
            if (MemorySegment.NULL.equals(pointer)) {
                return EMPTY;
            }
            try {
                var length = size.get(ValueLayout.JAVA_LONG, 0);
                if (length <= 0) {
                    return EMPTY;
                }
                return copyOut(pointer, length);
            } finally {
                release(pointer);
            }
        }
    }

    /// Every MIME type the clipboard is currently offering, in the order the
    /// owner advertised them.
    ///
    /// What a paste that found nothing needs in order to say **why**: "there is no
    /// image" and "there is an image in a format this toolkit cannot read" are the
    /// same answer from [#has] and completely different answers to a user.
    ///
    /// Cheap, like [#has] — the types are what the compositor has already been
    /// told, not a request to the owner.
    public List<String> types() {
        try (var arena = Arena.ofConfined()) {
            var count = arena.allocate(ValueLayout.JAVA_LONG);
            var array = sdlClipboardCalls.getClipboardMimeTypes().call(count);
            if (MemorySegment.NULL.equals(array)) {
                return List.of();
            }
            try {
                return readStrings(array, count.get(ValueLayout.JAVA_LONG, 0));
            } finally {
                // One free for the array and its strings together -- SDL
                // allocates them as one block.
                release(array);
            }
        }
    }

    /// Offers every type in `byMime` to the clipboard, replacing whatever this
    /// application was offering.
    ///
    /// The bytes are copied **into a native arena now** and handed out **later**:
    /// a caller's array may be modified or collected between the copy and the
    /// paste, and SDL's contract is that what the callback returns stays valid
    /// until it says otherwise.
    ///
    /// Several types at once because that is what one copy is: a board copying a
    /// shape offers its own format *and* a PNG, so pasting into another window
    /// keeps the shape and pasting into a chat window gets a picture. The order
    /// is the map's — SDL advertises them in the order given, and a well-behaved
    /// pasting application takes the first it understands.
    ///
    /// @return whether SDL accepted the offer
    /// @throws NullPointerException if any type or its bytes are null
    public boolean write(Map<String, byte[]> byMime) {
        Objects.requireNonNull(byMime, "byMime");
        if (byMime.isEmpty()) {
            return clear();
        }

        var id = nextOfferId.getAndIncrement();
        var arena = Arena.ofShared();
        try {
            var payloads = new LinkedHashMap<String, Payload>();
            var types = arena.allocate(ValueLayout.ADDRESS, byMime.size());
            var index = 0;
            for (var entry : byMime.entrySet()) {
                var mime = Objects.requireNonNull(entry.getKey(), "mime");
                var bytes = Objects.requireNonNull(entry.getValue(), "bytes");
                var payload = arena.allocate(Math.max(1, bytes.length));
                MemorySegment.copy(bytes, 0, payload, ValueLayout.JAVA_BYTE, 0, bytes.length);
                payloads.put(mime, new Payload(payload, bytes.length));
                types.setAtIndex(ValueLayout.ADDRESS, index++, arena.allocateFrom(mime));
            }
            offers.put(id, new Offer(arena, Map.copyOf(payloads)));

            var accepted = sdlClipboardCalls
                    .setClipboardData()
                    .call(dataStub, cleanupStub, MemorySegment.ofAddress(id), types, byMime.size());
            if (!accepted) {
                LOG.debug("SDL_SetClipboardData() refused: {}", Sdl.get().lastError());
                // Refused means the cleanup callback will never come, so the
                // arena is this call's to close.
                discard(id);
            }
            return accepted;
        } catch (RuntimeException | Error e) {
            discard(id);
            throw e;
        }
    }

    /// [#write(Map)] for one type.
    public boolean write(String mime, byte[] bytes) {
        return write(Map.of(Objects.requireNonNull(mime, "mime"), Objects.requireNonNull(bytes, "bytes")));
    }

    /// Drops whatever this application was offering.
    ///
    /// SDL answers this by calling the cleanup callback, which is what releases
    /// the arena — so there is nothing to free here.
    public boolean clear() {
        var accepted = sdlClipboardCalls.clearClipboardData().call();
        if (!accepted) {
            LOG.debug("SDL_ClearClipboardData() refused: {}", Sdl.get().lastError());
        }
        return accepted;
    }

    /// How many offers are still holding memory — for the test that this does not
    /// leak one per copy.
    public int liveOffers() {
        return offers.size();
    }

    // --- the two upcalls -----------------------------------------------------

    /// `const void* (void* userdata, const char* mime_type, size_t* size)`
    ///
    /// Somebody is pasting. Find the offer this callback was installed for, find
    /// the type they asked for, tell them how big it is and hand back the bytes.
    ///
    /// **Nothing may be thrown out of here.** An exception crossing back into C
    /// takes the process down, so every failure is a NULL and a zero size, which
    /// is SDL's own way of saying "I cannot produce that".
    private MemorySegment provide(MemorySegment userdata, MemorySegment mime, MemorySegment size) {
        try {
            var offer = offers.get(userdata.address());
            if (offer == null || MemorySegment.NULL.equals(mime)) {
                return writeSize(size, 0);
            }
            var payload = offer.payloads().get(readCString(mime));
            if (payload == null) {
                return writeSize(size, 0);
            }
            writeSize(size, payload.size());
            return payload.address();
        } catch (RuntimeException | Error e) {
            LOG.warn("a clipboard request failed and was answered with nothing", e);
            return MemorySegment.NULL;
        }
    }

    /// `void (void* userdata)` — this offer has been replaced or cleared, so the
    /// memory it was holding can go.
    private void forget(MemorySegment userdata) {
        try {
            discard(userdata.address());
        } catch (RuntimeException | Error e) {
            LOG.warn("a clipboard offer failed to release its memory", e);
        }
    }

    private void discard(long id) {
        var offer = offers.remove(id);
        if (offer != null) {
            offer.arena().close();
        }
    }

    /// Writes `length` into the `size_t*` SDL handed over, and returns NULL for
    /// the caller's convenience.
    // Restricted: a `size_t*` out-parameter arrives as a bare pointer and has to
    // be resized to the one value it points at before it can be written.
    @SuppressWarnings("restricted")
    private static MemorySegment writeSize(MemorySegment size, long length) {
        if (!MemorySegment.NULL.equals(size)) {
            size.reinterpret(ValueLayout.JAVA_LONG.byteSize()).set(ValueLayout.JAVA_LONG, 0, length);
        }
        return MemorySegment.NULL;
    }

    // Restricted: SDL hands back a pointer and a length, and the length is the
    // extent -- which is exactly what reinterpret is for.
    @SuppressWarnings("restricted")
    private static byte[] copyOut(MemorySegment pointer, long length) {
        return pointer.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
    }

    // Restricted: the same obligation SdlTray and SdlEventWatch take on. Neither
    // descriptor passes or returns anything by value.
    @SuppressWarnings("restricted")
    private MemorySegment upcallStub(String method, FunctionDescriptor descriptor, MethodType type) {
        try {
            var handle = MethodHandles.lookup().findVirtual(SdlClipboard.class, method, type);
            return LINKER.upcallStub(handle.bindTo(this), descriptor, stubs);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// One MIME type's bytes, in memory this offer owns.
    private record Payload(MemorySegment address, long size) {}

    /// What one copy put on the clipboard: the arena holding every type's bytes,
    /// and the types themselves.
    private record Offer(Arena arena, Map<String, Payload> payloads) {}

    /// `void SDL_free(void*)` — SDL's allocator, for the string it just handed
    /// over. See the note on this class.
    private void release(MemorySegment pointer) {
        sdlClipboardCalls.free().call(pointer);
    }

    // Restricted: a `char**` arrives as a bare pointer, and the count SDL just
    // reported is its extent.
    @SuppressWarnings("restricted")
    private static List<String> readStrings(MemorySegment array, long count) {
        if (count <= 0) {
            return List.of();
        }
        var pointers = array.reinterpret(count * ValueLayout.ADDRESS.byteSize());
        var types = new java.util.ArrayList<String>(Math.toIntExact(count));
        // A long counter for a long count. SDL will never report two billion
        // MIME types, but an int here would wrap rather than stop if it did.
        for (var i = 0L; i < count; i++) {
            var pointer = pointers.getAtIndex(ValueLayout.ADDRESS, i);
            if (!MemorySegment.NULL.equals(pointer)) {
                types.add(readCString(pointer));
            }
        }
        return List.copyOf(types);
    }

    // Restricted: the string's extent is not known until it is walked, which is
    // what reinterpret with an unbounded size is for. SDL guarantees NUL
    // termination for what SDL_GetClipboardText returns.
    @SuppressWarnings("restricted")
    private static String readCString(MemorySegment pointer) {
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
