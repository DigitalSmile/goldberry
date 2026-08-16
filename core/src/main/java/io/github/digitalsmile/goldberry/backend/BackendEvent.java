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
/// Pointer and keyboard events are here, split the way §7.1 asks: a key is a
/// key, and committed text is separate. Wheel and cursor are not.
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

    /// The pointer moved to a position inside the window.
    ///
    /// `x` and `y` are **logical**, window-relative — the same space an
    /// application lays out in, because SDL reports window coordinates and the
    /// display scale is applied when the frame is rasterized (ADR-0031).
    record PointerMoved(BackendWindow window, float x, float y) implements BackendEvent {
    }

    /// A pointer button went down.
    ///
    /// @param button SDL's index, 1-based and left-first; translated to a
    ///               toolkit button by the layer that dispatches
    /// @param clickCount 1 for a single click, 2 for a double — counted by the
    ///                   platform, so the toolkit keeps no timer of its own
    record PointerPressed(BackendWindow window, float x, float y, int button, int clickCount)
            implements BackendEvent {
    }

    /// A pointer button came up.
    record PointerReleased(BackendWindow window, float x, float y, int button, int clickCount)
            implements BackendEvent {
    }

    /// The pointer left the window.
    ///
    /// Separate from a move, because there is no position to report and `:hover`
    /// has to clear on the whole chain (§7.1).
    record PointerExited(BackendWindow window) implements BackendEvent {
    }

    /// A key went down.
    ///
    /// @param keycode  the platform's virtual keycode — translated to a [Key] by
    ///                 the layer that dispatches, so the SPI stays free of the
    ///                 toolkit's own naming
    /// @param modifiers the platform's modifier bitmask
    /// @param repeat   whether the platform is repeating a held key
    record KeyPressed(BackendWindow window, int keycode, int modifiers, boolean repeat)
            implements BackendEvent {
    }

    /// A key came up.
    record KeyReleased(BackendWindow window, int keycode, int modifiers) implements BackendEvent {
    }

    /// Text the platform has finished translating.
    ///
    /// Deliberately separate from [KeyPressed] (§7.1). One character can take
    /// several keys — a compose sequence, a dead key, an IME conversion — and a
    /// toolkit that derived text from keystrokes would be wrong in every language
    /// that needs one. The platform already knows the answer; this carries it.
    record TextInput(BackendWindow window, String text) implements BackendEvent {
    }
}
