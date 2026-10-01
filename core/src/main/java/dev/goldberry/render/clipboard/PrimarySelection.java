package dev.goldberry.render.clipboard;

import dev.goldberry.render.Backend;

/// The session's **primary selection** — X11's middle-click buffer, which Wayland
/// carries too — as the toolkit sees it ([ADR-0504]).
///
/// Selecting text puts it here and a middle click pastes it, with no `Ctrl+C` in
/// between. It is a second buffer beside the [Clipboard] and never the same one:
/// selecting a word must not replace what was copied, which is why X11 has two.
///
/// ## Optional, where the clipboard is not
///
/// [Backend#primarySelection()] answers **empty** wherever the platform has no
/// such thing — Windows, macOS, and SDL's own drivers off X11 and Wayland — and a
/// widget that finds it empty does nothing at all: it publishes no selection and
/// a middle click is not a paste. That is the difference from [Clipboard#none()],
/// which a caller can write to and read from without asking. A clipboard that is
/// always empty is honest; a middle button that moves the caret and pastes
/// nothing is a gesture from the wrong platform.
///
/// So a widget never learns **which** platform it is on. It asks whether there
/// is a primary selection, and the backend is the one thing that knows.
///
/// ## Text only
///
/// X11's selections can carry any target a clipboard can, and nothing that
/// selects in this toolkit selects anything but text. The byte half
/// [Clipboard#write(java.util.Map)] has would be an interface with no consumer.
///
/// ## Reads are not cheap
///
/// [#text()] is a round trip to the application that owns the selection, as a
/// clipboard read is; [#hasText()] is the cheap question. A write is cheap, and a
/// widget still makes one per **finished** selection rather than per pointer
/// move: on both X11 and Wayland every write is an ownership change every other
/// client on the desktop is told about.
///
/// Confined to the UI thread, like everything else in this package.
public interface PrimarySelection {

    /// Whether the primary selection holds any text.
    boolean hasText();

    /// The primary selection's text, or `""` when it holds none — empty rather
    /// than null, for [Clipboard#text()]'s reason.
    String text();

    /// Makes this application the primary selection's owner, holding `text`.
    ///
    /// @return whether the platform accepted it — a refusal is an outcome, as a
    ///         clipboard's is, and not an exception
    boolean text(String text);
}
