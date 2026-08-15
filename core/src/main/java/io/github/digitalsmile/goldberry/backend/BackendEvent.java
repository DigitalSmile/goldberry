package io.github.digitalsmile.goldberry.backend;

import java.util.Objects;

/// Something a backend observed, translated out of the platform's own vocabulary.
///
/// Sealed, so adding a case is a compile error everywhere it is handled rather
/// than a silently ignored event. That property is the reason this is a sealed
/// interface and not an enum plus a payload — input events are coming
/// (`docs/ARCHITECTURE.md` §7), and when they arrive every exhaustive switch
/// should stop compiling until it says what it does with them.
///
/// **Pointer and keyboard events are deliberately absent from this cut.** They
/// need the dispatch model in §7 — capture/target/bubble, pointer capture, the
/// `KeyEvent`/`TextEvent` split that keeps IME preedit possible — and designing
/// them against nothing but a blank window would be guesswork. What is here is
/// what a window needs to exist, resize, and close.
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
    record Resized(BackendWindow window, LogicalSize size, PhysicalSize physicalSize)
            implements BackendEvent {
        public Resized {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(physicalSize, "physicalSize");
        }
    }

    /// The window moved to a display with a different scale, or its display's
    /// scale changed under it.
    ///
    /// Separate from [Resized] because the logical size is unchanged: everything
    /// laid out in logical pixels stays where it is, and only the raster
    /// resolution moves. Dragging a window between a laptop panel and an external
    /// monitor is the ordinary case.
    record ScaleChanged(BackendWindow window, DisplayScale scale, PhysicalSize physicalSize)
            implements BackendEvent {
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
}
