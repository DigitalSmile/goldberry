package io.github.digitalsmile.goldberry.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;

/// The clipboard's byte half — ADR-0286.
///
/// Against the headless backend's in-memory clipboard, which is the one a test
/// gets. What is pinned here is the contract every caller is written against —
/// types go on, bytes come back, a write replaces rather than merges. The
/// platform's *laziness* and a *refusal* are the same clipboard's other two
/// halves and are `HeadlessClipboardTest` ([ADR-0407]).
///
/// The SDL side of the same contract, including the upcall a paste runs, is
/// `SdlClipboardTest` in `:natives`.
class ClipboardDataTest {

    private static final String SHAPE = "application/x-goldberry-shape";

    private HeadlessBackend backend;
    private Clipboard clipboard;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
        clipboard = backend.clipboard();
    }

    @Nested
    @DisplayName("bytes under a type")
    class Bytes {

        @Test
        @DisplayName("what was written is what is read")
        void roundTrips() {
            var bytes = new byte[] {1, 2, 3};

            assertTrue(clipboard.write(SHAPE, bytes));

            assertTrue(clipboard.has(SHAPE));
            assertArrayEquals(bytes, clipboard.read(SHAPE));
        }

        @Test
        @DisplayName("a type nobody wrote is empty rather than null")
        void missingIsEmpty() {
            assertFalse(clipboard.has(SHAPE));
            assertEquals(0, clipboard.read(SHAPE).length);
        }

        @Test
        @DisplayName("a write replaces the whole offer rather than adding to it")
        void writesReplace() {
            clipboard.write(SHAPE, new byte[] {1});
            clipboard.write("text/csv", new byte[] {2});

            // What a platform does: one application owns the clipboard and
            // advertises one set of types. A merge would let a stale type outlive
            // the copy that put it there.
            assertFalse(clipboard.has(SHAPE));
            assertTrue(clipboard.has("text/csv"));
        }

        @Test
        @DisplayName("one copy can offer several types")
        void severalTypes() {
            var byMime = new LinkedHashMap<String, byte[]>();
            byMime.put(SHAPE, new byte[] {1});
            byMime.put(Image.PNG_MIME, new byte[] {2});

            assertTrue(clipboard.write(byMime));

            assertTrue(clipboard.has(SHAPE));
            assertTrue(clipboard.has(Image.PNG_MIME));
        }

        @Test
        @DisplayName("what comes back is a copy, not the clipboard's own array")
        void readsAreCopies() {
            var written = new byte[] {1, 2, 3};
            clipboard.write(SHAPE, written);

            var read = clipboard.read(SHAPE);
            read[0] = 9;
            written[1] = 9;

            assertNotSame(written, read);
            assertArrayEquals(new byte[] {1, 2, 3}, clipboard.read(SHAPE), "neither end can reach in");
        }

        @Test
        @DisplayName("clearing drops the bytes and the text together")
        void clearing() {
            clipboard.text("copied");
            clipboard.write(SHAPE, new byte[] {1});

            assertTrue(clipboard.clear());

            assertFalse(clipboard.has(SHAPE));
            assertFalse(clipboard.hasText());
        }

        @Test
        @DisplayName("a clipboard that has no byte half says so rather than pretending")
        void theDefaultIsNothing() {
            var none = Clipboard.none();

            // The defaults on the interface, which is what a backend written
            // before this existed still gets.
            assertFalse(none.has(Image.PNG_MIME));
            assertEquals(0, none.read(Image.PNG_MIME).length);
            assertFalse(none.write(Image.PNG_MIME, new byte[] {1}));
            assertFalse(none.clear());
        }
    }

    @Nested
    @DisplayName("an image on it")
    class Images {

        @Test
        @DisplayName("an image goes on as a PNG and comes back as an image")
        void roundTripsAnImage() {
            RendererRequirement.enforce();
            var original = Image.ofArgb(2, 2, new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0x80FFFFFF});

            assertTrue(original.toClipboard(clipboard));

            assertTrue(Image.onClipboard(clipboard));
            var pasted = Image.fromClipboard(clipboard).orElseThrow();
            assertEquals(original.size(), pasted.size());
            assertEquals(0xFFFF0000, pasted.argb(0, 0));
            assertEquals(0x80FFFFFF, pasted.argb(1, 1), "and the alpha survived, which a BMP would not have");
        }

        @Test
        @DisplayName("it is written as image/png, which is what everything else reads")
        void writesTheUniversalType() {
            RendererRequirement.enforce();
            Image.ofArgb(1, 1, new int[] {0xFF123456}).toClipboard(clipboard);

            assertTrue(clipboard.has(Image.PNG_MIME));
        }

        @Test
        @DisplayName("a clipboard with no image is empty rather than an exception")
        void nothingToPaste() {
            clipboard.text("just text");

            assertFalse(Image.onClipboard(clipboard));
            assertTrue(Image.fromClipboard(clipboard).isEmpty());
        }

        @Test
        @DisplayName("a type this toolkit cannot decode is not offered as an image")
        void unreadableTypesAreNotImages() {
            // An application offering only TIFF is a paste Goldberry cannot do,
            // and finding nothing is better than handing bytes to a decoder that
            // will refuse them.
            //
            // **This used to be WebP**, and it is not any more: `docs/gaps.md`
            // G35a closed in ADR-0329 and the offered set grew by two the same
            // day. That is the set working rather than failing — it is a
            // statement about what can be decoded, so it moves when that does.
            clipboard.write("image/tiff", new byte[] {'I', 'I', 42, 0});

            assertFalse(Image.onClipboard(clipboard));
            assertTrue(Image.fromClipboard(clipboard).isEmpty());
        }

        @Test
        @DisplayName("WebP and GIF are offered now that there are codecs for them")
        void theTwoNewTypesAreOffered() {
            // The half of the rule above that is easy to forget: a type the
            // toolkit *can* read has to be picked up, or the codec is linked in
            // and unreachable from the one place a picture usually arrives
            // (`docs/gaps.md` G35a, [ADR-0329]).
            clipboard.write("image/webp", new byte[] {'R', 'I', 'F', 'F'});
            assertTrue(Image.onClipboard(clipboard));

            clipboard.clear();
            clipboard.write("image/gif", new byte[] {'G', 'I', 'F', '8', '9', 'a'});
            assertTrue(Image.onClipboard(clipboard));
        }

        @Test
        @DisplayName("bytes that were advertised and are not an image are reported, not swallowed")
        void aLyingClipboardIsReported() {
            RendererRequirement.enforce();
            clipboard.write(Image.PNG_MIME, new byte[] {'n', 'o', 't', ' ', 'a', ' ', 'p', 'n', 'g'});

            // The clipboard said it had a PNG. An application offering to paste
            // should be told that it lied rather than shown an empty Optional it
            // would read as "there was nothing there".
            assertThrows(
                    io.github.digitalsmile.goldberry.image.ImageDecodeException.class,
                    () -> Image.fromClipboard(clipboard));
        }

        @Test
        @DisplayName("nothing is refused quietly")
        void refusesNulls() {
            assertThrows(NullPointerException.class, () -> Image.fromClipboard(null));
            assertThrows(NullPointerException.class, () -> Image.onClipboard(null));
            var image = Image.ofArgb(1, 1, new int[] {0});
            assertThrows(NullPointerException.class, () -> image.toClipboard(null));
        }
    }

    @Nested
    @DisplayName("beside the text half")
    class WithText {

        @Test
        @DisplayName("text and bytes are independent")
        void independent() {
            clipboard.text("copied");
            clipboard.write(SHAPE, new byte[] {1});

            // A platform clipboard holds one offer, but SDL keeps text and data
            // separately and so does this: a widget that copies text must not
            // silently drop an image somebody else put there.
            assertEquals("copied", clipboard.text());
            assertArrayEquals(new byte[] {1}, clipboard.read(SHAPE));
        }
    }
}
