package io.github.digitalsmile.goldberry.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;

/// `text/uri-list` read as names — [ADR-0406].
///
/// The format is RFC 2483's and the interesting cases are all the ones a
/// well-formed list does not have: the separator another application actually
/// used, an entry that is not a file, and an entry that is not a URI. Each of
/// those is a decision recorded in the ADR and pinned by a test here.
class UriListTest {

    @Nested
    @DisplayName("the format")
    class Format {

        @Test
        @DisplayName("percent-decodes, which is the whole reason this type exists")
        void percentDecodes() {
            var list = UriList.parse("file:///tmp/my%20file.png\r\n");

            assertEquals(List.of(Path.of("/tmp/my file.png")), list.paths());
        }

        @Test
        @DisplayName("decodes non-ASCII names as UTF-8")
        void decodesUtf8() {
            // Percent-encoded UTF-8 is what every desktop writes; a reader that
            // guessed the platform charset would corrupt the name silently.
            var list = UriList.parse("file:///tmp/%D0%BF%D1%80.txt".getBytes(StandardCharsets.UTF_8));

            assertEquals(List.of(Path.of("/tmp/пр.txt")), list.paths());
        }

        @Test
        @DisplayName("reads CRLF, LF and a bare CR alike")
        void readsEverySeparator() {
            var crlf = UriList.parse("file:///a\r\nfile:///b\r\n");
            var lf = UriList.parse("file:///a\nfile:///b\n");
            var cr = UriList.parse("file:///a\rfile:///b");

            assertEquals(crlf.uris(), lf.uris());
            assertEquals(crlf.uris(), cr.uris());
            assertEquals(2, crlf.count());
        }

        @Test
        @DisplayName("skips comments and blank lines")
        void skipsComments() {
            var list = UriList.parse("""
                    # a comment, per RFC 2483
                    file:///tmp/a.png

                    file:///tmp/b.png
                    """);

            assertEquals(List.of(Path.of("/tmp/a.png"), Path.of("/tmp/b.png")), list.paths());
        }

        @Test
        @DisplayName("tolerates the trailing NUL an X11 client appends")
        void tolerantOfTrailingNul() {
            // Found by reading what actually arrives rather than the RFC: the
            // NUL lands inside the last line and makes it an illegal URI.
            var list = UriList.parse("file:///tmp/a.png\0");

            assertEquals(List.of(Path.of("/tmp/a.png")), list.paths());
        }

        @Test
        @DisplayName("keeps the order the other application listed")
        void keepsOrder() {
            var list = UriList.parse("file:///c\r\nfile:///a\r\nfile:///b\r\n");

            assertEquals(List.of(Path.of("/c"), Path.of("/a"), Path.of("/b")), list.paths());
        }

        @Test
        @DisplayName("writes CRLF, each entry terminated")
        void writesCrlf() {
            var path = Path.of("/tmp/my file.png");
            var list = UriList.of(path);

            // The URI is the path's own, not a literal. A bare `/tmp/...` is
            // drive-relative on Windows, so `toUri` absolutises it to
            // `file:///D:/tmp/...` there and to `file:///tmp/...` here — and a
            // hard-coded spelling asserts which machine ran the test rather than
            // what this class writes. What it writes is the terminator.
            assertEquals(path.toUri() + "\r\n", list.text());
            assertTrue(list.text().endsWith("\r\n"), "every entry is terminated, including the last");
            assertTrue(
                    list.text().contains("my%20file"), "a space is percent-encoded rather than left to split the line");
            assertArrayEquals(list.text().getBytes(StandardCharsets.UTF_8), list.encode());
        }

        @Test
        @DisplayName("round-trips a list of paths through its own bytes")
        void roundTrips() {
            var paths = List.of(Path.of("/tmp/a b.png"), Path.of("/tmp/пр.txt"));

            // Compared absolute, because a URI is: `Path.toUri` resolves a
            // relative path against the working directory, so a drive-relative
            // `\tmp\a b.png` on Windows comes back as `D:\tmp\a b.png` — the
            // same file, spelled in full. The round trip preserves the file, not
            // the abbreviation.
            var expected = paths.stream().map(Path::toAbsolutePath).toList();
            assertEquals(expected, UriList.parse(UriList.of(paths).encode()).paths());
        }
    }

    @Nested
    @DisplayName("an entry that is not a local file")
    class NotAFile {

        @Test
        @DisplayName("is kept as a URI and left out of the paths")
        void keptButNotAPath() {
            // A drag out of a browser is exactly this list.
            var list = UriList.parse("https://example.com/x.png\r\nfile:///tmp/a.png\r\n");

            assertEquals(2, list.count(), "a non-file URI is a good entry in a good list");
            assertEquals(List.of(Path.of("/tmp/a.png")), list.paths());
        }

        @Test
        @DisplayName("is dropped when it has no scheme at all")
        void aBarePathIsNotAUri() {
            // Deliberately not guessed at: reading `/tmp/x` as a local path is
            // how a name written by another machine becomes a file on this one.
            var list = UriList.parse("/tmp/x\r\nfile:///tmp/a.png\r\n");

            assertEquals(List.of(URI.create("file:///tmp/a.png")), list.uris());
        }

        @Test
        @DisplayName("is dropped, with the rest of the list kept, when it is not a URI")
        void anUnparseableLineIsSkipped() {
            // `%ZZ` is a malformed escape pair and `a b` has a space in it —
            // both make URI's own parser throw.
            var list = UriList.parse("file:///tmp/%ZZ\r\nfile:///tmp/a b\r\nfile:///tmp/good.png\r\n");

            assertEquals(List.of(Path.of("/tmp/good.png")), list.paths());
        }

        @Test
        @DisplayName("is dropped when the name is one this file system refuses")
        void aNulByteInTheNameIsSkipped() {
            // The case ADR-0330 decided for drag-and-drop, arriving through the
            // other door: %00 is a legal percent escape and NUL is not a legal
            // path, so URI accepts what Path.of will not.
            var list = UriList.parse("file:///tmp/a%00b\r\nfile:///tmp/good.png\r\n");

            assertEquals(1, list.paths().size());
            assertEquals(Path.of("/tmp/good.png"), list.paths().getFirst());
        }

        @Test
        @DisplayName("keeps a file: URI that names this machine as localhost")
        void localhostIsThisMachine() {
            // RFC 8089 blesses `file://localhost/x` and Path.of refuses it
            // outright — "URI has an authority component". Dropping the entry
            // would be the toolkit inventing a failure.
            var list = UriList.parse("file://localhost/tmp/a.png\r\n");

            assertEquals(List.of(Path.of("/tmp/a.png")), list.paths());
        }

        @Test
        @DisplayName("drops a file: URI that names another host")
        void anotherHostIsNotLocal() {
            // Refused by UriList itself rather than by Path.of, which accepts it
            // on Windows as a UNC share -- see `namesAnotherHost`.
            var list = UriList.parse("file://fileserver/share/a.png\r\n");

            assertEquals(1, list.count());
            assertTrue(list.paths().isEmpty(), "opening the wrong file is worse than opening none");
        }
    }

    @Nested
    @DisplayName("the value")
    class Value {

        @Test
        @DisplayName("is a copy, so the list it was built from cannot change it")
        void copies() {
            var uris = new ArrayList<URI>();
            uris.add(URI.create("file:///a"));
            var list = new UriList(uris);

            uris.add(URI.create("file:///b"));

            assertEquals(1, list.count());
            assertThrows(UnsupportedOperationException.class, () -> list.uris().add(URI.create("file:///c")));
        }

        @Test
        @DisplayName("is empty rather than null when nothing was listed")
        void empty() {
            assertTrue(UriList.EMPTY.isEmpty());
            assertTrue(UriList.parse("# nothing but a comment\r\n").isEmpty());
            assertTrue(UriList.parse("").isEmpty());
        }

        @Test
        @DisplayName("refuses a null anywhere it takes one")
        void refusesNull() {
            assertThrows(NullPointerException.class, () -> UriList.parse((String) null));
            assertThrows(NullPointerException.class, () -> UriList.parse((byte[]) null));
            assertThrows(NullPointerException.class, () -> new UriList(null));
            assertThrows(NullPointerException.class, () -> UriList.of((Path) null));
        }
    }

    @Nested
    @DisplayName("on a clipboard")
    class OnAClipboard {

        @Test
        @DisplayName("goes on and comes back off as paths")
        void roundTripsThroughAClipboard() {
            try (var backend = new HeadlessBackend()) {
                var clipboard = backend.clipboard();
                var paths = List.of(Path.of("/tmp/a b.png"), Path.of("/tmp/c.png"));
                // Absolute for the reason `roundTrips` gives: a URI has no
                // relative spelling to come back as.
                var expected = paths.stream().map(Path::toAbsolutePath).toList();

                assertTrue(UriList.of(paths).toClipboard(clipboard));

                assertTrue(UriList.onClipboard(clipboard));
                assertEquals(List.of(UriList.MIME), clipboard.types());
                assertEquals(expected, UriList.fromClipboard(clipboard).paths());
            }
        }

        @Test
        @DisplayName("is empty, not absent, when the clipboard is offering none")
        void emptyWhenNothingIsOffered() {
            try (var backend = new HeadlessBackend()) {
                assertFalse(UriList.onClipboard(backend.clipboard()));
                assertTrue(UriList.fromClipboard(backend.clipboard()).isEmpty());
            }
        }

        @Test
        @DisplayName("reports a refused write rather than throwing")
        void refusalIsReported() {
            try (var backend = new HeadlessBackend()) {
                backend.clipboard().refuseWrites(true);

                assertFalse(UriList.of(Path.of("/tmp/a.png")).toClipboard(backend.clipboard()));
                assertFalse(UriList.onClipboard(backend.clipboard()));
            }
        }
    }
}
