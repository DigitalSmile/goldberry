package io.github.digitalsmile.goldberry.render;

import java.util.List;
import java.util.Map;

/// The session's clipboard, as the toolkit sees it.
///
/// The facility `docs/ARCHITECTURE.md` §4 listed and [Backend] deliberately left
/// out until something needed it (ADR-0019). `text-input` needs it, and what it
/// needs is exactly this: read the text, write the text, ask whether there is
/// any.
///
/// ## Text, and bytes under a MIME type
///
/// Text is a value and the rest of a clipboard is a **transfer negotiation**: the
/// owning application advertises the types it can produce and is asked to produce
/// one when somebody pastes. That is why the two halves of this interface do not
/// look alike — [#text(String)] copies, and [#write(Map)] offers (ADR-0286).
///
/// The byte half is deliberately *not* typed. A clipboard carries an image, a
/// document's own format, a file list and whatever else two applications have
/// agreed on, and the only thing common to all of them is a MIME type and some
/// bytes. What turns those into an image is
/// [Image.fromClipboard][io.github.digitalsmile.goldberry.image.Image#fromClipboard],
/// which lives beside the decoder rather than here: a backend implementing this
/// interface should not have to know what a PNG is.
///
/// A **file list** goes the same way. `text/uri-list` is bytes under a type like
/// anything else, and what turns those bytes into names is [UriList] — a value
/// beside this interface rather than a method on it, because percent-decoding a
/// URI is not something a backend should have an opinion about either
/// ([ADR-0406]).
///
/// Every byte method has a **default that does nothing**, so a backend with no
/// data clipboard — or one written before this existed — is honest rather than
/// broken: it reports that it holds nothing and accepts nothing.
///
/// ## Not a `Property`, and not watched
///
/// Nothing here reports a change. X11 and Wayland deliver clipboard ownership
/// changes and Windows has a viewer chain, so "the clipboard changed" is
/// knowable — but the only thing that would use it is a paste button greying
/// itself out, and a paste button that asks [#hasText()] when its menu opens gets
/// the same answer for none of the machinery. A widget that wants the current
/// text asks for it.
///
/// ## Reads are not cheap
///
/// [#text()] is a **round trip to the application that owns the clipboard** on
/// X11 and Wayland: the compositor asks the owner to serialise, and the owner may
/// be a browser that is busy. It is not something to call per frame, per
/// keystroke, or to poll. [#hasText()] is the cheap question and is answered from
/// what the platform already knows.
///
/// Confined to the UI thread, like everything else in this package.
public interface Clipboard {

    /// Whether the clipboard holds any text.
    ///
    /// The question a paste command asks before offering itself. Cheap — see the
    /// note on this interface.
    boolean hasText();

    /// The clipboard's text, or `""` when it holds none.
    ///
    /// Empty rather than null: "there is nothing to paste" and "what was copied
    /// was empty" are the same paste, and a caller that had to distinguish them
    /// would have nothing different to do.
    String text();

    /// Puts `text` on the clipboard, replacing whatever was there.
    ///
    /// @return whether the platform accepted it. A refusal is a real outcome
    ///         rather than an exception — a compositor can decline, and a copy
    ///         that did not happen must not take the window down with it.
    boolean text(String text);

    // --- bytes under a MIME type ---------------------------------------------

    /// Whether the clipboard can produce `mime`.
    ///
    /// The cheap question, like [#hasText()] and for the same reason: it is
    /// answered from what the platform has already advertised, where [#read] asks
    /// the owner to serialise.
    default boolean has(String mime) {
        return false;
    }

    /// The clipboard's bytes for `mime`, or an empty array when it holds none.
    ///
    /// Empty rather than null, for [#text()]'s reason.
    ///
    /// **Not cheap.** On X11 and Wayland this is a round trip to the application
    /// that owns the clipboard, which may be a browser that is busy.
    default byte[] read(String mime) {
        return new byte[0];
    }

    /// Every MIME type the clipboard is currently offering, in the order its
    /// owner advertised them.
    ///
    /// Cheap, like [#has]. What it is **for** is the failing case: a paste that
    /// found no image needs to be able to say whether there was nothing there at
    /// all or something in a format this toolkit cannot read, and those are the
    /// same answer from [#has] alone (ADR-0286).
    ///
    /// Empty when the platform cannot say, which is not the same as an empty
    /// clipboard — some platforms report the text half here and some do not.
    default List<String> types() {
        return List.of();
    }

    /// Offers `bytes` as `mime`, replacing whatever this application was
    /// offering.
    ///
    /// @return whether the platform accepted the offer
    default boolean write(String mime, byte[] bytes) {
        return write(Map.of(mime, bytes));
    }

    /// Offers several types at once — what one copy usually is.
    ///
    /// A board copying a shape offers its own format **and** a PNG, so that
    /// pasting back into the board keeps the shape and pasting into a chat window
    /// gets a picture. Order matters: the platform advertises them in this order
    /// and a well-behaved pasting application takes the first type it
    /// understands, so a `LinkedHashMap` says something a `Map.of` does not.
    ///
    /// An empty map clears.
    ///
    /// @return whether the platform accepted the offer
    default boolean write(Map<String, byte[]> byMime) {
        return false;
    }

    /// Drops whatever this application was offering, text included.
    ///
    /// @return whether the platform accepted it
    default boolean clear() {
        return false;
    }

    /// A clipboard that is always empty and accepts nothing.
    ///
    /// What a backend with no platform clipboard reports, and what a headless
    /// test gets unless it asks for something better. Not an `Optional` on
    /// [Backend]: every caller of a missing clipboard would write this class, and
    /// a `copy` that quietly did nothing is the honest behaviour of a session
    /// that has nowhere to put it.
    static Clipboard none() {
        return new Clipboard() {

            @Override
            public boolean hasText() {
                return false;
            }

            @Override
            public String text() {
                return "";
            }

            @Override
            public boolean text(String text) {
                return false;
            }

            @Override
            public String toString() {
                return "Clipboard[none]";
            }
        };
    }
}
