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
/// The title bar and three of the gallery's five screens are here because every
/// value in them flows through `bind=` and every gesture through `change=`: they
/// need no Java at all, and a designer can move a badge or rename a class in them
/// without a compiler. **A screen is a file**, so adding one to the gallery is a
/// file and a line ([ADR-0110](../../../../../../../book/src/adr/0110-the-showcase-is-a-gallery-of-screens.md)).
///
/// The other two screens are **not**, and both reasons are instructive rather
/// than incidental:
///
/// - [Content]'s Undo and Reset buttons are disabled when the click count is
///   zero, and §8's markup has no expressions. `disabled=#true` is a constant; a
///   document cannot say "disabled when this property is zero" and is not going
///   to grow a way to.
/// - [TabsDemo]'s strip has a list that **changes** — tabs are added and closed
///   while the window is open — and KDL is data. It can write three tabs, not
///   "however many the model has".
public final class Panes {

    private Panes() {
    }

    /// The window's top bar — a label, a bound count, and the theme button.
    public static Widget titleBar(KdlInflater<Widget> inflater) {
        return inflate(inflater, "titlebar.kdl");
    }

    /// The **Controls** screen: §3's controls whose value is a state.
    public static Widget controls(KdlInflater<Widget> inflater) {
        return inflate(inflater, "controls.kdl");
    }

    /// The **Values** screen: §3's controls whose value is a number, and the two
    /// that report one back.
    public static Widget values(KdlInflater<Widget> inflater) {
        return inflate(inflater, "values.kdl");
    }

    /// The **Overlays** screen: the two places something can float over a window.
    public static Widget overlays(KdlInflater<Widget> inflater) {
        return inflate(inflater, "overlays.kdl");
    }

    /// The **Panels** screen: §5's containers.
    ///
    /// The purest case for a document there is. Nothing on that screen holds a
    /// value — they are all containers — so it needs no `bind=`, no `change=`
    /// and no Java at all, and the two widgets on it with state keep it
    /// themselves.
    public static Widget panels(KdlInflater<Widget> inflater) {
        return inflate(inflater, "panels.kdl");
    }

    /// The **Forms** screen: §4's `text-input` in every state it has.
    ///
    /// A document for [#panels]'s reason and a stronger one: a field holds its
    /// own text, caret and undo stack, so a screen full of them needs no more
    /// wiring than a screen full of cards. The two that *are* wired are wired the
    /// way a real form is — `bind=` down and `change=` back up — which is what
    /// makes this screen the demonstration that a field does not adopt the echo
    /// of its own keystroke.
    public static Widget forms(KdlInflater<Widget> inflater) {
        return inflate(inflater, "forms.kdl");
    }

    private static Widget inflate(KdlInflater<Widget> inflater, String document) {
        return inflater.inflate(KdlParser.resource(Panes.class, document).getFirst());
    }
}
