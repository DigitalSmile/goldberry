package io.github.digitalsmile.goldberry.render.event;

import java.util.Objects;

import io.github.digitalsmile.goldberry.render.desktop.SystemTheme;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;

/// Something a backend observed, translated out of the platform's own vocabulary.
///
/// Sealed, so adding a case is a compile error everywhere it is handled rather
/// than a silently ignored event. That property is the reason this is a sealed
/// interface and not an enum plus a payload — input events are coming
/// (`docs/ARCHITECTURE.md` §7), and when they arrive every exhaustive switch
/// should stop compiling until it says what it does with them.
///
/// Pointer, wheel and keyboard events are here, split the way §7.1 asks: a key
/// is a key, and committed text is separate. The cursor travels the other way and
/// so is a method on [BackendWindow] rather than an event.
public sealed interface BackendEvent {

    /// The window this concerns.
    BackendWindow window();

    /// The user asked to close the window — a title-bar button, `Alt+F4`, a
    /// window-menu item.
    ///
    /// A request, not a fact: the window is still open, and the application
    /// decides. Ignoring this is how "unsaved changes?" prompts work.
    record CloseRequested(BackendWindow window) implements BackendEvent {
        public CloseRequested {
            Objects.requireNonNull(window, "window");
        }
    }

    /// The window's size changed. Carries both sizes because the pair is what
    /// callers need and deriving one from the other invites the rounding to be
    /// redone slightly differently somewhere else.
    record Resized(BackendWindow window, LogicalSize size, PhysicalSize physicalSize) implements BackendEvent {
        public Resized {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(physicalSize, "physicalSize");
        }
    }

    /// The window's top-left corner moved on the desktop.
    ///
    /// **Not a resize and not a scale change**: nothing inside the window moved,
    /// which is why no repaint follows one. What changes is where the window
    /// *is*, and therefore where the screen's edges are in the window's own
    /// coordinates — which is the space a popup is placed in. A menu flipped
    /// above its button because there was no room below it has to be asked the
    /// question again when the window it hangs off is dragged up the screen
    /// ([ADR-0270]).
    ///
    /// The position is in the desktop's logical coordinates, the same space
    /// [BackendWindow#position()] and [BackendWindow#workArea()] answer in.
    record Moved(BackendWindow window, LogicalPoint position) implements BackendEvent {
        public Moved {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(position, "position");
        }
    }

    /// The window moved to a display with a different scale, or its display's
    /// scale changed under it.
    ///
    /// Separate from [Resized] because the logical size is unchanged: everything
    /// laid out in logical pixels stays where it is, and only the raster
    /// resolution moves. Dragging a window between a laptop panel and an external
    /// monitor is the ordinary case.
    record ScaleChanged(BackendWindow window, DisplayScale scale, PhysicalSize physicalSize) implements BackendEvent {
        public ScaleChanged {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(scale, "scale");
            Objects.requireNonNull(physicalSize, "physicalSize");
        }
    }

    /// The window's contents were lost and must be redrawn — uncovered, restored,
    /// or the compositor discarded the buffer.
    record Exposed(BackendWindow window) implements BackendEvent {
        public Exposed {
            Objects.requireNonNull(window, "window");
        }
    }

    /// The desktop's light-or-dark setting changed — `docs/gaps.md` G26,
    /// [ADR-0322].
    ///
    /// **It carries a window, and the setting does not.** The change is the
    /// session's: on every platform with a sunset schedule it happens once a day,
    /// while the application is running, and it concerns every window at once. It
    /// is delivered per window for the reason `QUIT` is delivered as one
    /// `CloseRequested` per window — a [io.github.digitalsmile.goldberry.Host] is
    /// per window, so that is where an application is listening — and a backend
    /// that has two windows open sends two of these with the same theme in them.
    record SystemThemeChanged(BackendWindow window, SystemTheme theme) implements BackendEvent {
        public SystemThemeChanged {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(theme, "theme");
        }
    }

    /// The backend is ready for the next frame, in response to
    /// [BackendWindow#requestFrame()].
    ///
    /// This is the vsync-aligned heartbeat the frame loop runs on. It arrives
    /// only when asked for: an idle application draws nothing.
    record FrameDue(BackendWindow window) implements BackendEvent {
        public FrameDue {
            Objects.requireNonNull(window, "window");
        }
    }

    /// The pointer moved to a position inside the window.
    ///
    /// `x` and `y` are **logical**, window-relative — the same space an
    /// application lays out in, because SDL reports window coordinates and the
    /// display scale is applied when the frame is rasterized (ADR-0031).
    /// @param modifiers which modifier keys were held — see [#modifiers] on
    ///                  [PointerWheel] for why every pointer event carries them
    record PointerMoved(BackendWindow window, float x, float y, int modifiers) implements BackendEvent {}

    /// A pointer button went down.
    ///
    /// @param button SDL's index, 1-based and left-first; translated to a
    ///               toolkit button by the layer that dispatches
    /// @param clickCount 1 for a single click, 2 for a double — counted by the
    ///                   platform, so the toolkit keeps no timer of its own
    record PointerPressed(BackendWindow window, float x, float y, int button, int clickCount, int modifiers)
            implements BackendEvent {}

    /// A pointer button came up.
    record PointerReleased(BackendWindow window, float x, float y, int button, int clickCount, int modifiers)
            implements BackendEvent {}

    /// The wheel turned, or a touchpad scrolled.
    ///
    /// Carries its own position: a wheel event can be the first thing a window
    /// hears after the pointer entered it, and scrolling whatever was under the
    /// pointer last time would be wrong.
    ///
    /// The deltas are in **lines**, positive down and right — normalized here so
    /// that no two backends have to agree on anything harder. SDL reports the
    /// opposite sign vertically and inverts both axes when the platform is set to
    /// "natural" scrolling; that is undone at the boundary, because a toolkit
    /// whose scroll direction depends on a system preference is broken for
    /// exactly the users who changed it.
    ///
    /// Lines, not pixels, because SDL reports no pixel-precise delta and
    /// ADR-0115
    /// declines to go around it for one. They are fractional on a touchpad, which
    /// is what stops a trackpad scrolling in jerks.
    ///
    /// `ticksX` and `ticksY` are the same turn accumulated by the platform into
    /// **whole detents**, for the consumers that want a discrete step rather than
    /// a distance. Not a rounding of the deltas — the running fraction is kept
    /// across events, so a slow trackpad eventually reports a click that no
    /// single event's float is large enough to produce.
    /// @param modifiers the platform's modifier bitmask, as
    ///                  [io.github.digitalsmile.goldberry.input.key.Modifiers#fromSdl]
    ///                  reads it. Every pointer event carries it because §3 asks a
    ///                  knob for a "modifier for fine adjustment" and §2.3 asks
    ///                  for `Ctrl+click`, and because a backend is the only layer
    ///                  that can read it *at the moment the event happened* —
    ///                  latching it from the last key event leaves it stuck down
    ///                  when a window loses focus mid-chord ([ADR-0089])
    record PointerWheel(
            BackendWindow window, float x, float y, float deltaX, float deltaY, int ticksX, int ticksY, int modifiers)
            implements BackendEvent {

        /// A wheel turn whose detents are the truncation of its deltas — what a
        /// backend with no accumulator of its own can honestly say.
        public PointerWheel(BackendWindow window, float x, float y, float deltaX, float deltaY, int modifiers) {
            this(window, x, y, deltaX, deltaY, (int) deltaX, (int) deltaY, modifiers);
        }
    }

    /// The pointer left the window.
    ///
    /// Separate from a move, because there is no position to report and `:hover`
    /// has to clear on the whole chain (§7.1).
    record PointerExited(BackendWindow window) implements BackendEvent {}

    /// The window gained or lost the keyboard focus.
    ///
    /// Reported per window and never per application, because that is what every
    /// platform reports: opening a popup sends a *lost* for the window under it
    /// and a *gained* for the popup itself, one after the other. So "the
    /// application lost focus" is a conclusion drawn from the whole set and not
    /// an event — see `Launcher`, which is the only thing that needs to draw it
    /// (ADR-0144).
    record FocusChanged(BackendWindow window, boolean focused) implements BackendEvent {}

    /// The window became maximized, or stopped being.
    ///
    /// **The only truth about the state** (ADR-0252). Maximizing is a *request* a
    /// window manager may refuse, delay or grant in part, so a window is
    /// maximized when the platform says it is and not when the application asked
    /// — which is what makes "remember whether the user maximized it" answerable
    /// at all.
    ///
    /// One event for both directions, because SDL reports them as two
    /// (`MAXIMIZED` and `RESTORED`) and every consumer wants the boolean. The
    /// same shape [FocusChanged] already has, for the same reason.
    record MaximizedChanged(BackendWindow window, boolean maximized) implements BackendEvent {}

    /// A key went down.
    ///
    /// @param keycode  the platform's virtual keycode — translated to a [Key] by
    ///                 the layer that dispatches, so the SPI stays free of the
    ///                 toolkit's own naming
    /// @param modifiers the platform's modifier bitmask
    /// @param repeat   whether the platform is repeating a held key
    record KeyPressed(BackendWindow window, int keycode, int modifiers, boolean repeat) implements BackendEvent {}

    /// A key came up.
    record KeyReleased(BackendWindow window, int keycode, int modifiers) implements BackendEvent {}

    /// Text the platform has finished translating.
    ///
    /// Deliberately separate from [KeyPressed] (§7.1). One character can take
    /// several keys — a compose sequence, a dead key, an IME conversion — and a
    /// toolkit that derived text from keystrokes would be wrong in every language
    /// that needs one. The platform already knows the answer; this carries it.
    record TextInput(BackendWindow window, String text) implements BackendEvent {}

    /// The composition an input method is assembling, before the user has
    /// accepted it — `docs/gaps.md` G15.
    ///
    /// **Not text, and not an edit.** A Japanese, Chinese or Korean user types
    /// several keys, sees an underlined string being built with a candidate list
    /// beside it, and only what they accept arrives as [TextInput]. A toolkit
    /// that inserted this into the document would be inserting characters the
    /// user has not chosen and then deleting them again — visible as flicker,
    /// wrong in the undo history, and wrong in anything watching the value
    /// (ADR-0289).
    ///
    /// An **empty** `text` means the composition has ended, with or without a
    /// [TextInput] before it: an accepted candidate commits, and an abandoned one
    /// does not.
    ///
    /// @param window the window with keyboard focus
    /// @param text   the composition so far, or `""` when it has ended
    /// @param start  where the selection inside it starts, as a **char** offset
    ///               into `text`, or `-1` when the platform reports none
    /// @param length how many chars of it are selected, or `-1` for none
    record TextEditing(BackendWindow window, String text, int start, int length) implements BackendEvent {}

    /// One file of a drag-and-drop gesture landed on the window —
    /// `docs/gaps.md` G35b, [ADR-0330].
    ///
    /// **One event per file, not per gesture**, because that is what every
    /// platform reports: dropping three files raises three of these and then one
    /// [FileDropCompleted]. Assembling them into a single
    /// [io.github.digitalsmile.goldberry.input.drop.FileDrop] is the toolkit's
    /// job rather than the backend's, so it is written once and can be tested
    /// without a desktop.
    ///
    /// The path is a **string** here and a `Path` above it, for the reason a
    /// keycode is an int at this seam: what the platform handed over is a name in
    /// its own encoding, and turning it into something Java's file system agrees
    /// with is a conversion with a failure mode — a backend should not be the
    /// place it is decided.
    ///
    /// @param x the drop's window-relative x in logical pixels
    /// @param y the same, vertically
    record FileDropped(BackendWindow window, String path, float x, float y) implements BackendEvent {
        public FileDropped {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(path, "path");
        }
    }

    /// The drag-and-drop gesture ended — every file that was coming has arrived.
    ///
    /// Raised whether or not any [FileDropped] preceded it: a drag that crossed
    /// the window and left drops nothing, and something still has to clear the
    /// half-built gesture. It carries the last position the platform reported, so
    /// a drop whose files arrived without coordinates still knows where it was.
    record FileDropCompleted(BackendWindow window, float x, float y) implements BackendEvent {
        public FileDropCompleted {
            Objects.requireNonNull(window, "window");
        }
    }
}
