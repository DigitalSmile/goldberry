package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Widget;

/// The parts of the window that are **documents**, inflated once.
///
/// Once and not per build: a document does not change while the window is open,
/// and re-parsing a file on every rebuild would put a tokenizer on the frame
/// path. What comes out is an ordinary widget — the element tree cannot tell it
/// came from markup, which is the whole point of §9.
///
/// ## What is in markup and what is not
///
/// The title bar and the sidebar are here because every value in them flows
/// through `bind=` and every gesture through `change=`: they need no Java at all,
/// and a designer can move a badge or rename a class in them without a compiler.
///
/// [Content] is **not**, and the reason is instructive rather than incidental —
/// its Undo and Reset buttons are disabled when the click count is zero, and
/// §8's markup has no expressions. `disabled=#true` is a constant. A document
/// cannot say "disabled when this property is zero" and is not going to grow a
/// way to, so the pane that needs one stays in Java
/// ([ADR-0094](../../../../../../../book/src/adr/0094-name-the-overload-not-the-allocation.md)).
public final class Panes {

    private Panes() {
    }

    /// The window's top bar — a label, a bound count, and the theme button.
    public static Widget titleBar(KdlInflater<Widget> inflater) {
        return inflate(inflater, "titlebar.kdl");
    }

    /// The theme picker, the status chips, the bound status line, and every
    /// control in the window.
    public static Widget sidebar(KdlInflater<Widget> inflater) {
        return inflate(inflater, "sidebar.kdl");
    }

    private static Widget inflate(KdlInflater<Widget> inflater, String document) {
        return inflater.inflate(KdlParser.resource(Panes.class, document).getFirst());
    }
}
