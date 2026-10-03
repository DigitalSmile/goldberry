package dev.goldberry.example.ui.input;

/// The few colours the input screen's canvases draw in: Nord's frost, green and
/// yellow, which read on both of the showcase's themes.
final class InputColors {

    /// Lines the pointer drew.
    static final int INK = 0xFF88C0D0;

    /// Where the pointer is now.
    static final int ACCENT = 0xFFA3BE8C;

    /// Where a press went down.
    static final int WARN = 0xFFEBCB8B;

    /// The grid under everything.
    static final int MUTED = 0x804C566A;

    private InputColors() {}
}
