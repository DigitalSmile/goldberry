package io.github.digitalsmile.goldberry.render;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;

/// A `text/uri-list` — the way every desktop puts a **file list** on the
/// clipboard ([ADR-0406]).
///
/// The clipboard's byte half already carried this: `text/uri-list` is bytes under
/// a MIME type like anything else and has worked since [ADR-0286]. What was
/// missing is the twenty lines that turn those bytes into names, which every
/// application would otherwise write, and each of them slightly differently —
/// percent-decoding is the part that is easy to get wrong and impossible to
/// notice, because it only shows on a file with a space in it.
///
/// ## The format, and where it bites
///
/// RFC 2483: one URI per line, lines separated by CRLF, and a line whose first
/// character is `#` is a comment. The URIs are percent-encoded, so
/// `/tmp/my file.png` travels as `file:///tmp/my%20file.png` and a naive reader
/// hands the file system a name with a `%20` in it.
///
/// In practice the separator is whatever the other application felt like: a bare
/// LF is common, a trailing CRLF is usual and a trailing NUL happens on X11. All
/// four are read here. What is **written** is CRLF, each entry terminated — the
/// format as specified, because the one thing this side controls is being
/// conservative.
///
/// ## Not every entry is a file
///
/// A drag out of a browser is a `text/uri-list` of `https:` URIs; a mail client
/// offers `mailto:`. Those are perfectly good entries in a perfectly good list,
/// so [#uris()] keeps them and [#paths()] is the *file* half — the conversion,
/// with the entries that are not local files left out. An application that asked
/// for paths and got fewer than it expected can look at both and say which it
/// was, which is the same argument [Clipboard#types()] is on the interface for.
///
/// A line that is not a URI at all is **dropped with a log** rather than
/// throwing: the list came from another application over a protocol with no
/// schema, and a paste of nine good names that failed on the tenth is worse than
/// a paste of nine ([ADR-0330] decided this for the drag-and-drop path, whose
/// names arrive from the same desktops).
///
/// ## Reading one is a paste
///
/// [#fromClipboard] goes through [Clipboard#read], which is a round trip to
/// whichever application owns the clipboard. [#onClipboard] is the cheap question.
///
/// @param uris the entries, in the order they were listed; every one a URI, not
///             every one a file
public record UriList(List<URI> uris) {

    private static final Logger LOG = Logs.of(UriList.class);

    /// The MIME type a file list travels under, on every desktop this toolkit
    /// runs on.
    public static final String MIME = "text/uri-list";

    /// The scheme [#paths()] converts. Compared case-insensitively, because RFC
    /// 3986 says a scheme is — and `FILE:///tmp/x` is what at least one desktop
    /// file manager writes.
    private static final String FILE_SCHEME = "file";

    /// The separator a list is **written** with — RFC 2483's, terminating every
    /// entry rather than joining them.
    private static final String CRLF = "\r\n";

    /// An empty list, for the paste that found nothing.
    public static final UriList EMPTY = new UriList(List.of());

    public UriList {
        Objects.requireNonNull(uris, "uris");
        // Copied rather than merely checked: this is a value that is compared and
        // handed around, and a list the caller can still add to is neither.
        uris = List.copyOf(uris);
    }

    /// Parses a `text/uri-list` out of the bytes a clipboard or a drop handed
    /// over.
    ///
    /// UTF-8, which is what the percent-decoding of a `file:` URI produces and
    /// what every desktop writes. Nothing here can throw on bad input — see the
    /// note on this type.
    public static UriList parse(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    /// [#parse(byte[])] for text that has already been decoded.
    public static UriList parse(String text) {
        Objects.requireNonNull(text, "text");
        var out = new ArrayList<URI>();
        // CRLF is the format; LF alone and CR alone are what actually arrives.
        for (var raw : text.split("\r\n|\r|\n", -1)) {
            // Some X11 clients terminate the whole payload with a NUL, which
            // lands on the last line and makes it an illegal URI.
            var line = raw.replace("\u0000", "").strip();
            if (line.isEmpty() || line.startsWith("#")) {
                // A comment, or the blank left by the trailing CRLF that a
                // well-formed list ends with.
                continue;
            }
            try {
                var uri = new URI(line);
                if (!uri.isAbsolute()) {
                    // A bare path -- `/tmp/x`, or `C:\x`. Deliberately *not*
                    // guessed at: a list entry with no scheme is not a URI, and
                    // reading one as a local path is how a Windows name written
                    // by another machine becomes a file this one would create.
                    LOG.debug("a uri-list entry has no scheme and was skipped: {}", line);
                    continue;
                }
                out.add(uri);
            } catch (URISyntaxException e) {
                LOG.debug("a uri-list entry is not a URI and was skipped: {}", line, e);
            }
        }
        return new UriList(out);
    }

    /// The list a copy of these files puts on the clipboard.
    public static UriList of(List<Path> paths) {
        Objects.requireNonNull(paths, "paths");
        var out = new ArrayList<URI>(paths.size());
        for (var path : paths) {
            // Absolute, percent-encoded, and `file:` -- Path.toUri does all
            // three, and a relative path resolves against the working directory,
            // which is the only reading of it another application could use.
            out.add(Objects.requireNonNull(path, "path").toUri());
        }
        return new UriList(out);
    }

    /// [#of(List)] for the common case.
    public static UriList of(Path... paths) {
        return of(Arrays.asList(Objects.requireNonNull(paths, "paths")));
    }

    /// The entries that are local files, as paths.
    ///
    /// Shorter than [#uris()] whenever the list held something else, and shorter
    /// again when an entry is a name this file system will not accept — a NUL
    /// byte survives percent-encoding, and `file:///tmp/a%00b` is a legal URI and
    /// not a legal path. Skipped with a log, for the reason given on this type.
    ///
    /// Computed on each call rather than held: a paste is a deliberate act and a
    /// list is a handful of entries.
    public List<Path> paths() {
        var out = new ArrayList<Path>(uris.size());
        for (var uri : uris) {
            if (!FILE_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                continue;
            }
            try {
                out.add(Path.of(withoutLocalAuthority(uri)));
            } catch (RuntimeException e) {
                // IllegalArgumentException for a name Java's file system refuses
                // or an opaque `file:relative.txt`; FileSystemNotFoundException
                // for a `file:` URI this JVM has no provider for. All of them are
                // "that entry is not a file here", which is not worth three arms.
                LOG.debug("a uri-list entry is not a usable path and was skipped: {}", uri, e);
            }
        }
        return List.copyOf(out);
    }

    /// `file://localhost/tmp/x` with the authority taken off.
    ///
    /// RFC 8089 blesses both `file:///x` and `file://localhost/x` and Java's file
    /// system accepts only the first — `Path.of` refuses an authority outright,
    /// including the `localhost` that means "this machine". Dropping an entry over
    /// that would be the toolkit inventing a failure, so the one authority that
    /// *is* this machine is normalised away and any other is left to fail: a
    /// `file://fileserver/share` is a name for something on another host, and
    /// pretending it is local would open the wrong file rather than none.
    private static URI withoutLocalAuthority(URI uri) {
        var authority = uri.getAuthority();
        if (authority == null || !authority.equalsIgnoreCase("localhost")) {
            return uri;
        }
        return URI.create(uri.getScheme() + "://" + uri.getRawPath());
    }

    /// Whether the list has no entries at all.
    public boolean isEmpty() {
        return uris.isEmpty();
    }

    /// How many entries were listed — including the ones that are not files.
    public int count() {
        return uris.size();
    }

    /// The list in its wire form: one entry per line, CRLF-terminated.
    public String text() {
        var out = new StringBuilder();
        for (var uri : uris) {
            // The *raw* form. A URI that came off the clipboard percent-encoded
            // goes back out the way it arrived, and one built from a Path was
            // encoded by Path.toUri.
            out.append(uri.toASCIIString()).append(CRLF);
        }
        return out.toString();
    }

    /// [#text()] as the bytes that go on a clipboard.
    public byte[] encode() {
        // US-ASCII would do -- a percent-encoded URI has no other characters in
        // it -- but UTF-8 is what the type is read back as and agreeing with
        // ourselves costs nothing.
        return text().getBytes(StandardCharsets.UTF_8);
    }

    /// Whether `clipboard` is offering a file list.
    ///
    /// The cheap question: it asks what has already been advertised and fetches
    /// nothing.
    public static boolean onClipboard(Clipboard clipboard) {
        Objects.requireNonNull(clipboard, "clipboard");
        return clipboard.has(MIME);
    }

    /// The file list on `clipboard`, empty when it is offering none.
    ///
    /// **This is a paste** — see [Clipboard#read].
    ///
    /// Empty rather than an `Optional`, for the reason [Clipboard#text()] is: "no
    /// file list was offered" and "a file list with nothing in it" are the same
    /// paste, and a caller that had to tell them apart would have nothing
    /// different to do. An application that does care asks [#onClipboard] first.
    public static UriList fromClipboard(Clipboard clipboard) {
        Objects.requireNonNull(clipboard, "clipboard");
        var bytes = clipboard.read(MIME);
        return bytes.length == 0 ? EMPTY : parse(bytes);
    }

    /// Puts this list on `clipboard` as `text/uri-list`, replacing whatever this
    /// application was offering.
    ///
    /// @return whether the platform accepted it
    public boolean toClipboard(Clipboard clipboard) {
        Objects.requireNonNull(clipboard, "clipboard");
        return clipboard.write(MIME, encode());
    }
}
