package io.github.digitalsmile.goldberry.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessClipboard;

/// The two things the in-memory clipboard could not model — [ADR-0407].
///
/// [ADR-0286] built the byte half and said so in its own consequences: "what it
/// cannot model is laziness or a refusal, and it does not pretend to". Both are
/// real parts of the platform's contract that every caller is written against, so
/// both were branches no test on any machine ever took. This is where they are
/// taken.
///
/// The round trip the same clipboard does — types on, bytes off, a write replaces
/// — is `ClipboardDataTest`.
class HeadlessClipboardTest {

    private static final String SHAPE = "application/x-goldberry-shape";

    private HeadlessBackend backend;
    private HeadlessClipboard clipboard;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
        clipboard = backend.clipboard();
    }

    @AfterEach
    void tearDown() {
        backend.close();
    }

    @Nested
    @DisplayName("an offer is lazy")
    class Laziness {

        @Test
        @DisplayName("produces nothing when nobody pastes")
        void nothingIsSerialisedUntilItIsRead() {
            var produced = new AtomicInteger();

            assertTrue(clipboard.offer(Map.of(SHAPE, counting(produced, new byte[] {1, 2, 3}))));

            // The whole point. On a real desktop the compositor holds a callback
            // and calls it if and when somebody pastes; a copy that nobody pastes
            // never serialises anything (ADR-0286).
            assertEquals(0, produced.get(), "an offer that nobody read must not have produced its bytes");
        }

        @Test
        @DisplayName("answers has() and types() out of what was advertised")
        void theCheapQuestionsStayCheap() {
            var produced = new AtomicInteger();
            clipboard.offer(Map.of(SHAPE, counting(produced, new byte[] {1})));

            assertTrue(clipboard.has(SHAPE));
            assertEquals(List.of(SHAPE), clipboard.types());
            assertFalse(clipboard.has("image/png"));

            // These are the questions a paste button asks when its menu opens.
            // A clipboard that serialised to answer them would make asking
            // expensive, which is exactly what Clipboard's own note forbids.
            assertEquals(0, produced.get());
        }

        @Test
        @DisplayName("produces the bytes when something reads them")
        void readProduces() {
            var produced = new AtomicInteger();
            clipboard.offer(Map.of(SHAPE, counting(produced, new byte[] {1, 2, 3})));

            assertArrayEquals(new byte[] {1, 2, 3}, clipboard.read(SHAPE));
            assertEquals(1, produced.get());
        }

        @Test
        @DisplayName("produces them again for a second paste")
        void everyReadProduces() {
            var produced = new AtomicInteger();
            clipboard.offer(Map.of(SHAPE, counting(produced, new byte[] {7})));

            clipboard.read(SHAPE);
            clipboard.read(SHAPE);

            // SDL's request callback runs once per paste, so the supplier does
            // too. Caching here would hide an application that serialises
            // something expensive on every paste.
            assertEquals(2, produced.get());
        }

        @Test
        @DisplayName("keeps the order the types were offered in")
        void keepsOrder() {
            var byMime = new LinkedHashMap<String, Supplier<byte[]>>();
            byMime.put(SHAPE, () -> new byte[] {1});
            byMime.put("image/png", () -> new byte[] {2});

            clipboard.offer(byMime);

            // The order is part of the offer: a pasting application takes the
            // first type it understands (ADR-0286).
            assertEquals(List.of(SHAPE, "image/png"), clipboard.types());
        }

        @Test
        @DisplayName("replaces the previous offer, so nothing of it is left to read")
        void replaces() {
            var stale = new AtomicInteger();
            clipboard.offer(Map.of(SHAPE, counting(stale, new byte[] {1})));

            clipboard.offer(Map.of("image/png", () -> new byte[] {2}));

            assertFalse(clipboard.has(SHAPE));
            assertArrayEquals(new byte[0], clipboard.read(SHAPE));
            assertEquals(0, stale.get(), "a replaced offer is never asked to serialise");
        }

        @Test
        @DisplayName("treats a supplier that produced nothing as an empty read")
        void aFailedSerialisationReadsEmpty() {
            clipboard.offer(Map.of(SHAPE, () -> new byte[0]));

            // What a real one does: an upcall that cannot answer returns NULL and
            // a zero size, which reaches the pasting side as nothing at all
            // rather than as an exception.
            assertArrayEquals(new byte[0], clipboard.read(SHAPE));
        }

        @Test
        @DisplayName("is still eager for write(), which already holds the bytes")
        void writeIsEagerBecauseItsSignatureIs() {
            var bytes = new byte[] {1, 2, 3};

            assertTrue(clipboard.write(SHAPE, bytes));
            bytes[0] = 9;

            // Copied at write time, because the array is the caller's. There is
            // no laziness left to model here: write(Map) is handed the bytes.
            assertArrayEquals(new byte[] {1, 2, 3}, clipboard.read(SHAPE));
        }

        @Test
        @DisplayName("refuses a null type or supplier")
        void refusesNull() {
            var nulls = new HashMap<String, Supplier<byte[]>>();
            nulls.put(SHAPE, null);

            assertThrows(NullPointerException.class, () -> clipboard.offer(null));
            assertThrows(NullPointerException.class, () -> clipboard.offer(nulls));
        }

        private Supplier<byte[]> counting(AtomicInteger produced, byte[] bytes) {
            return () -> {
                produced.incrementAndGet();
                return bytes.clone();
            };
        }
    }

    @Nested
    @DisplayName("a refusal")
    class Refusal {

        @Test
        @DisplayName("is off by default, so nothing that worked before changes")
        void offByDefault() {
            assertFalse(clipboard.isRefusingWrites());
            assertTrue(clipboard.text("copied"));
            assertTrue(clipboard.write(SHAPE, new byte[] {1}));
            assertTrue(clipboard.clear());
        }

        @Test
        @DisplayName("makes every write say false rather than throw")
        void everyWriteRefuses() {
            clipboard.refuseWrites(true);

            // The branch an application writes for "the compositor declined".
            // Before this seam it was only ever taken on a real desktop.
            assertFalse(clipboard.text("copied"));
            assertFalse(clipboard.write(SHAPE, new byte[] {1}));
            assertFalse(clipboard.write(Map.of(SHAPE, new byte[] {1})));
            assertFalse(clipboard.offer(Map.of(SHAPE, () -> new byte[] {1})));
            assertFalse(clipboard.clear());
        }

        @Test
        @DisplayName("leaves what was already on the clipboard alone")
        void changesNothing() {
            clipboard.text("kept");
            clipboard.write(SHAPE, new byte[] {1, 2, 3});

            clipboard.refuseWrites(true);
            clipboard.text("lost");
            clipboard.write(SHAPE, new byte[] {9});
            clipboard.clear();

            // A declined offer is not a cleared clipboard. An application that
            // read `false` and then found its own copy gone would be looking at
            // a bug this class had invented.
            assertEquals("kept", clipboard.text());
            assertArrayEquals(new byte[] {1, 2, 3}, clipboard.read(SHAPE));
        }

        @Test
        @DisplayName("does not stop a paste")
        void readsStillWork() {
            clipboard.write(SHAPE, new byte[] {4});
            clipboard.refuseWrites(true);

            assertTrue(clipboard.has(SHAPE));
            assertEquals(List.of(SHAPE), clipboard.types());
            assertArrayEquals(new byte[] {4}, clipboard.read(SHAPE));
        }

        @Test
        @DisplayName("can be turned back off")
        void canBeTurnedOff() {
            clipboard.refuseWrites(true);
            assertFalse(clipboard.text("no"));

            clipboard.refuseWrites(false);

            assertFalse(clipboard.isRefusingWrites());
            assertTrue(clipboard.text("yes"));
            assertEquals("yes", clipboard.text());
        }
    }

    @Nested
    @DisplayName("the seams")
    class Seams {

        @Test
        @DisplayName("are reachable without a cast, because clipboard() is narrowed")
        void narrowedReturnType() {
            // The reason the return type is HeadlessClipboard and not Clipboard:
            // a test that had to cast would be the only place in the test that
            // knew which backend it was running on -- fileDialogs()' argument.
            HeadlessClipboard narrowed = backend.clipboard();

            assertFalse(narrowed.isRefusingWrites());
        }

        @Test
        @DisplayName("are confined to the UI thread like everything else here")
        void uiThreadOnly() throws Exception {
            var failure = new AtomicReference<Throwable>();
            var thread = new Thread(() -> {
                try {
                    clipboard.offer(Map.of(SHAPE, () -> new byte[] {1}));
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            thread.start();
            thread.join();

            assertTrue(failure.get() instanceof BackendException, "a seam is still an SPI call: " + failure.get());
        }
    }
}
