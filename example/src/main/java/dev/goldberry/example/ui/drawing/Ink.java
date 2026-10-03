package dev.goldberry.example.ui.drawing;

/// The colours the Drawing screen's painters draw with.
///
/// The chart palette's first slots, so the drawings sit beside the charts rather
/// than inventing colours of their own. A painter is handed numbers rather than
/// the cascade unless it asks for a `CanvasStyle`, and these are the numbers.
///
/// Read more: [The painter](https://goldberry.dev/docs/components/drawing.html#the-painter).
final class Ink {

    /// The line every drawing is made of.
    static final int LINE = 0xFF88C0D0;

    /// What the eye is meant to land on.
    static final int ACCENT = 0xFFA3BE8C;

    /// A third hue, for the shapes beside the first two.
    static final int WARN = 0xFFEBCB8B;

    /// Grids, frames and what is said under a picture.
    static final int MUTED = 0xFF4C566A;

    /// A sticky's paper, which is yellow because a sticky is.
    static final int PAPER = 0xFFEBCB8B;

    /// The text written on the paper.
    static final int WRITING = 0xFF2E3440;

    private Ink() {}
}
