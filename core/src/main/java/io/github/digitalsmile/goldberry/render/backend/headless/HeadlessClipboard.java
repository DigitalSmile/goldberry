package io.github.digitalsmile.goldberry.render.backend.headless;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.github.digitalsmile.goldberry.render.Clipboard;

/// The session clipboard a headless test gets — in memory, **lazy**, and able to
/// refuse ([ADR-0407]).
///
/// Not [Clipboard#none()]: a test that copies and pastes should be testing the
/// widget's editing model, and against a clipboard that accepts nothing every
/// such test passes for the wrong reason. This one behaves like a session's —
/// what was last written is what is read.
///
/// ## An offer is lazy, here as well
///
/// A platform clipboard does not hold bytes, it holds a **callback**: the
/// compositor asks the owning application to serialise when somebody pastes, and
/// if nobody ever does, the bytes are never produced ([ADR-0286]). That is the
/// contract every caller of [Clipboard#write] is written against, so it is the
/// contract this class keeps: [#offer] takes suppliers, and [#read] is the only
/// thing that calls one.
///
/// ```java
/// var produced = new AtomicInteger();
/// clipboard.offer(Map.of(UriList.MIME, () -> {
///     produced.incrementAndGet();
///     return list.encode();
/// }));
/// assertTrue(clipboard.has(UriList.MIME));
/// assertEquals(0, produced.get());       // advertised, not serialised
/// ```
///
/// Each read calls the supplier **again**, because each paste on a real desktop
/// runs the callback again. A test that wants to count pastes can.
///
/// [Clipboard#write] is still eager where its own signature is: the caller
/// already holds the array, so there is nothing left to defer. It is stored as a
/// supplier over a copy, so the one code path serves both.
///
/// ## A refusal is a real outcome
///
/// Every write on the interface returns a `boolean` because a compositor can
/// decline, and until [#refuseWrites] existed that `false` was unreachable
/// without a desktop — so the branch an application writes for it was never
/// executed anywhere. It is a **test seam and not a policy**: the default accepts
/// everything, exactly as before.
///
/// A refused write changes nothing. What was on the clipboard is still on it,
/// which is what a declined offer leaves behind.
///
/// Confined to the UI thread, like the rest of this backend.
public final class HeadlessClipboard implements Clipboard {

    private final HeadlessBackend backend;

    /// The text half. A [StringBuilder] rather than a `String` field because
    /// `text(null)` clears and the two operations read better on one object.
    private final StringBuilder text = new StringBuilder();

    /// The byte half — what this application is **offering**, as the platform
    /// holds it: a type and something that can produce the bytes for it.
    ///
    /// Insertion-ordered, because the order a caller offered its types in is part
    /// of the offer and [Clipboard#types()] promises it back.
    private final Map<String, Supplier<byte[]>> offered = new LinkedHashMap<>();

    private boolean refusing;

    HeadlessClipboard(HeadlessBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    // --- the test seams ------------------------------------------------------

    /// Offers `byMime` **lazily**: nothing calls a supplier until something reads
    /// the type it was offered under.
    ///
    /// The shape a platform clipboard actually has — see the note on this class.
    /// Replaces whatever was being offered, exactly as [Clipboard#write(Map)]
    /// does.
    ///
    /// @return whether the offer was accepted, so a test drives the refusing case
    ///         through the same call
    /// @throws NullPointerException if any type or its supplier is null
    public boolean offer(Map<String, Supplier<byte[]>> byMime) {
        backend.requireUiThread();
        Objects.requireNonNull(byMime, "byMime");
        if (refusing) {
            return false;
        }
        offered.clear();
        byMime.forEach((mime, supplier) ->
                offered.put(Objects.requireNonNull(mime, "mime"), Objects.requireNonNull(supplier, "supplier")));
        return true;
    }

    /// Makes every write refuse, as a compositor that declined one does.
    ///
    /// Reads are unaffected: a clipboard that will not take a new offer still has
    /// the old one on it. Off by default — this exists so the `false` branch is
    /// reachable from a test, not to model a platform that says no.
    ///
    /// @param value whether writes refuse from now on
    /// @return this clipboard, for the fluent set-up [HeadlessFileDialogs] reads
    ///         the same way
    public HeadlessClipboard refuseWrites(boolean value) {
        backend.requireUiThread();
        this.refusing = value;
        return this;
    }

    /// Whether writes are currently refusing.
    public boolean isRefusingWrites() {
        backend.requireUiThread();
        return refusing;
    }

    // --- Clipboard -----------------------------------------------------------

    @Override
    public boolean hasText() {
        backend.requireUiThread();
        return !text.isEmpty();
    }

    @Override
    public String text() {
        backend.requireUiThread();
        return text.toString();
    }

    @Override
    public boolean text(String value) {
        backend.requireUiThread();
        if (refusing) {
            return false;
        }
        text.setLength(0);
        text.append(value == null ? "" : value);
        return true;
    }

    @Override
    public boolean has(String mime) {
        backend.requireUiThread();
        return offered.containsKey(mime);
    }

    @Override
    public byte[] read(String mime) {
        backend.requireUiThread();
        var supplier = offered.get(mime);
        if (supplier == null) {
            return new byte[0];
        }
        // This is the paste. A supplier that answers with nothing is a
        // serialisation that failed, which on a real desktop arrives as an empty
        // read rather than as an exception -- see SdlClipboard's upcall.
        var bytes = supplier.get();
        return bytes == null ? new byte[0] : bytes;
    }

    @Override
    public List<String> types() {
        backend.requireUiThread();
        return List.copyOf(offered.keySet());
    }

    @Override
    public boolean write(Map<String, byte[]> byMime) {
        backend.requireUiThread();
        Objects.requireNonNull(byMime, "byMime");
        var suppliers = new LinkedHashMap<String, Supplier<byte[]>>();
        byMime.forEach((mime, bytes) -> {
            // Copied at write time, because these bytes are the caller's and a
            // clipboard that changed when the caller reused its array would be
            // modelling nothing. Copied again per read, because what a platform
            // hands back is a copy: a test that mutated what it pasted and saw
            // the clipboard change would be learning about this class.
            var copy = Objects.requireNonNull(bytes, "bytes").clone();
            suppliers.put(Objects.requireNonNull(mime, "mime"), copy::clone);
        });
        return offer(suppliers);
    }

    @Override
    public boolean clear() {
        backend.requireUiThread();
        if (refusing) {
            return false;
        }
        offered.clear();
        text.setLength(0);
        return true;
    }

    @Override
    public String toString() {
        return "Clipboard[headless, " + text.length() + " chars, " + offered.size() + " types"
                + (refusing ? ", refusing writes]" : "]");
    }
}
