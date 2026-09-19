package io.github.digitalsmile.goldberry.input.drop;

import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.LogicalPoint;

/// Text dropped on a window, and where — [ADR-0408].
///
/// [FileDrop]'s sibling, and deliberately its shape: a desktop reports a text
/// drop as a beginning, a moving position, one event per **line** and the same end
/// a file drop has. Reassembling that run is the same arithmetic, so it is done in
/// the same place ([ADR-0330]).
///
/// ## Lines, not a string
///
/// This is the part that is not a stylistic choice. SDL tokenises a dropped text
/// payload on `\r\n` and raises one `SDL_EVENT_DROP_TEXT` per token — on Wayland,
/// on Windows, on macOS — so by the time anything in Java can see it, a two-line
/// selection is two payloads and **the separators no longer exist**. A record
/// holding one `String` would have to invent them.
///
/// So the lines are the value, and [#text()] is the convenience that joins them
/// with `\n` for the caller who wants one string. That `\n` is this toolkit's
/// choice and not the source's: whether the user's selection ended in a newline,
/// and whether it used `\r\n`, is information SDL discarded. For the overwhelmingly
/// common drop — a word, a URL, a line out of a terminal — there is one line and
/// nothing to reconstruct.
///
/// ## The coordinates
///
/// [LogicalPoint], window-relative, the same space [FileDrop#at()] is in and for
/// the same reason: a drop is "put this *here*", and text dropped on an editor
/// lands at a caret position that has to be hit-tested.
///
/// ## Nothing is interpreted
///
/// The lines are bytes the other application chose to call text. Whether they are
/// a URL, a number, a path in disguise or twelve megabytes of log is the
/// business of whoever accepted the drop. In particular a text drop is **not** a
/// file drop with a different spelling: a file manager that drags a file sends
/// `SDL_EVENT_DROP_FILE`, and an application that wants to treat a dropped
/// `file:` URL as a file can say so itself with
/// [UriList][io.github.digitalsmile.goldberry.render.UriList] ([ADR-0406]).
///
/// @param lines the dropped text, one entry per line the platform reported, in
///              order; never empty and no entry empty
/// @param at    where in the window the drop landed, in logical pixels
public record TextDrop(List<String> lines, LogicalPoint at) {

    public TextDrop {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(at, "at");
        // Copied, for FileDrop's reason: this is a value, and a list the caller
        // can still add to is not one.
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a text drop with no text in it is not a drop; the toolkit raises one"
                    + " only when at least one line arrived");
        }
    }

    /// The whole drop as one string, lines joined with `\n`.
    ///
    /// What a text field inserts. The separator is this toolkit's — see the note
    /// on this type — and for a one-line drop, which is nearly all of them, there
    /// is no separator to be wrong about.
    public String text() {
        return String.join("\n", lines);
    }

    /// The first line — what an application that expects one thing wants, and the
    /// common case by a long way.
    public String first() {
        return lines.getFirst();
    }

    /// How many lines were dropped.
    public int count() {
        return lines.size();
    }
}
