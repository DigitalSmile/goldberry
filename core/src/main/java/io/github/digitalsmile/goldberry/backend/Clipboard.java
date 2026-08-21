package io.github.digitalsmile.goldberry.backend;

/// The session's clipboard, as the toolkit sees it.
///
/// The facility `docs/ARCHITECTURE.md` §4 listed and [Backend] deliberately left
/// out until something needed it (ADR-0019). `text-input` needs it, and what it
/// needs is exactly this: read the text, write the text, ask whether there is
/// any.
///
/// ## Text only, and why that is not a placeholder
///
/// A clipboard can hold images, files and arbitrary MIME types, and every one of
/// those is a *transfer negotiation* rather than a value — the owning application
/// advertises formats and serialises on demand, which means an interface that
/// admits them has to admit lazy providers, format lists and cancellation. None
/// of that has a consumer here. `text-input`, `text-area` and `code-input` all
/// copy and paste strings; when a widget wants an image on the clipboard, this
/// interface will be told about it by that widget, in the same way this one was
/// told about text.
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
